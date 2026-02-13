package com.example.seoulapi.controller;

import com.example.seoulapi.scheduler.CoordinateFixScheduler;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.ResponseEntity;

import com.example.seoulapi.model.RoomEntity;
import com.example.seoulapi.service.RentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.example.seoulapi.scheduler.RentScheduler;


// 매물 조회/필터링 API
@RestController
@RequestMapping("/api/rent")
public class RentController {

    private final RentService rentService;
    private final CoordinateFixScheduler coordinateFixScheduler;
    private final RentScheduler rentScheduler;

    @Value("${seoul.api.key}")
    private String seoulApiKey;

    @Value("${seoul.api.startYear}")
    private int startYear;

    @Value("${seoul.api.endYear}")
    private int endYear;

    public RentController(RentService rentService, CoordinateFixScheduler coordinateFixScheduler, RentScheduler rentScheduler) {
        this.rentService = rentService;
        this.coordinateFixScheduler = coordinateFixScheduler;
        this.rentScheduler = rentScheduler;
    }

    // 수동 갱신 실행
    @GetMapping("/update-now")
    public ResponseEntity<String> triggerManualUpdate() {
        try {
            System.out.println("==== 수동 실행 시작 ====");
            rentScheduler.updateRecentContracts();
            System.out.println("==== 수동 실행 완료 ====");
            return ResponseEntity.ok("수동 갱신 실행 완료");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("수동 갱신 실행 중 오류: " + e.getMessage());
        }
    }

    // 모든 매물 조회
    @GetMapping("/all")
    public List<RoomEntity> getAllRooms() {
        return rentService.getAllRooms();
    }

    // 구 단위 매물 조회
    @GetMapping("/gu")
    public List<RoomEntity> getRoomsByGu(@RequestParam String gu) {
        return rentService.getRoomsByGu(gu);
    }

    // 동 단위 매물 조회
    @GetMapping("/dong")
    public List<RoomEntity> getRoomsByDong(@RequestParam String dong) {
        return rentService.getRoomsByDong(dong);
    }

    // 보증금 범위 검색
    @GetMapping("/deposit")
    public List<RoomEntity> getRoomsByDepositRange(@RequestParam int min, @RequestParam int max) {
        return rentService.getRoomsByDepositRange(min, max);
    }

    // 월세 범위 검색
    @GetMapping("/rentFee")
    public List<RoomEntity> getRoomsByRentFeeRange(@RequestParam int min, @RequestParam int max) {
        return rentService.getRoomsByRentFeeRange(min, max);
    }

    // 좌표변환
    @PostMapping("/fix-coordinates")
    public ResponseEntity<String> fixCoordinates() {
        rentService.fixCoordinatesSequentially();
        return ResponseEntity.ok("좌표 변환 작업 시작");
    }

    // 초기 적재 - 서울시 전월세 정보 API에서 데이터 불러와 DB 저장
    @GetMapping("/loadAll")
    public String loadAllSeoulData() {

        int totalInserted = 0;
        try {
            System.out.println("==== 최근 1년치 데이터 적재 시작 ====");
            System.out.println("대상 연도: " + this.startYear + " ~ " + this.endYear);


            for (int year = this.startYear; year <= this.endYear; year++) {
                // 연도별 총 데이터 개수 확인용
                String countUrl = "http://openapi.seoul.go.kr:8088/" + seoulApiKey
                        + "/json/tbLnOpendataRentV/1/1?RCPT_YR=" + year;

                URL url = new URL(countUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(sb.toString());

                int total = root.path("tbLnOpendataRentV").path("list_total_count").asInt();
                System.out.println("총 " + year + "년 데이터 개수: " + total);

                // 페이징 루프
                for (int start = 1; start <= total; start += 1000) {
                    int end = Math.min(start + 999, total);
                    boolean success = false;
                    int retryCount = 0;

                    while (!success && retryCount < 3) {
                        try {
                            String urlStr = "http://openapi.seoul.go.kr:8088/"
                                    + seoulApiKey + "/json/tbLnOpendataRentV/"
                                    + start + "/" + end
                                    + "?RCPT_YR=" + year;

                            System.out.println("요청 URL = " + urlStr);

                            URL pageUrl = new URL(urlStr);
                            HttpURLConnection pageConn = (HttpURLConnection) pageUrl.openConnection();
                            pageConn.setRequestMethod("GET");
                            pageConn.setConnectTimeout(10000); // 연결 제한
                            pageConn.setReadTimeout(20000);    // 응답 제한

                            BufferedReader pageBr = new BufferedReader(new InputStreamReader(pageConn.getInputStream(), "UTF-8"));
                            StringBuilder pageSb = new StringBuilder();
                            String pageLine;
                            while ((pageLine = pageBr.readLine()) != null) {
                                pageSb.append(pageLine);
                            }
                            pageBr.close();

                            JsonNode pageRoot = mapper.readTree(pageSb.toString());
                            JsonNode rows = pageRoot.path("tbLnOpendataRentV").path("row");

                            if (rows.isMissingNode() || rows.size() == 0) {
                                System.out.println("[경고] " + start + "~" + end + " 구간: 데이터 없음, 스킵");
                                success = true;
                                continue;
                            }

                            // 최근 1년치 필터링
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                            LocalDate oneYearAgo = LocalDate.now().minusYears(1);
                            List<JsonNode> rowList = new java.util.ArrayList<>();

                            for (JsonNode row : rows) {
                                String ctrtDay = row.path("CTRT_YMD").asText("");
                                if (!ctrtDay.isEmpty()) {
                                    try {
                                        LocalDate contractDate = LocalDate.parse(ctrtDay, formatter);
                                        if (contractDate.isBefore(oneYearAgo)) {
                                            continue; // 1년 이전 계약은 스킵
                                        }
                                    } catch (Exception ignored) {
                                        continue; // 파싱 안 되면 스킵
                                    }
                                }
                                rowList.add(row);
                            }

                            rentService.saveRooms(rowList);
                            totalInserted += rowList.size();

                            System.out.println("[" + year + "] 적재 완료: " + start + "~" + end + " (누적: " + totalInserted + ")");
                            success = true;

                        } catch (Exception e) {
                            retryCount++;
                            System.err.println("️ [" + year + "] " + start + "~" + end + " 구간 실패 (" + retryCount + "회차)");
                            e.printStackTrace();
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }

                    if (!success) {
                        System.err.println(" [" + year + "] " + start + "~" + end + " 구간 3회 실패 -> 스킵");
                    }
                }
            }

            System.out.println(" 최근 1년치 데이터 적재 완료 ");
            return "최근 1년치 데이터 적재 완료 (총 " + totalInserted + "건). ";
        } catch (Exception e) {
            e.printStackTrace();
            return "서울시 데이터 적재 실패: " + e.getMessage();
        }
    }


    // DB 전체 삭제
    @DeleteMapping
    public String deleteAllRooms() {
        rentService.deleteAllRooms();
        return "모든 매물 삭제 완료";
    }
}
