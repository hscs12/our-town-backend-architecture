package com.example.seoulapi.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class ODsayApiUtil {

    @Value("${odsay.api.key}")
    private String odsayApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();


    // 추천 리스트용 - 소요 시간만 반환
    public Map<String, Integer> getTransitSummary(
            List<Map<String, Object>> origins, double destLng, double destLat
    ) {
        Map<String, Integer> result = new HashMap<>();

        for (Map<String, Object> origin : origins) {
            double startX = (double) origin.get("lng");
            double startY = (double) origin.get("lat");

            String url = String.format(
                    "https://api.odsay.com/v1/api/searchPubTransPathT?SX=%f&SY=%f&EX=%f&EY=%f"
                            + "&searchType=1"      // 도착 시간 기준 검색
                            + "&endTime=0900"      // 오전 9시 기준
                            + "&apiKey=%s",
                    startX, startY, destLng, destLat, odsayApiKey
            );

            try {
                ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
                Map<String, Object> body = response.getBody();
                if (body == null || !body.containsKey("result")) continue;

                Map<String, Object> resultObj = (Map<String, Object>) body.get("result");
                List<Map<String, Object>> paths = (List<Map<String, Object>>) resultObj.get("path");
                if (paths == null || paths.isEmpty()) continue;

                Map<String, Object> info = (Map<String, Object>) paths.get(0).get("info");
                int totalTime = ((Number) info.get("totalTime")).intValue();
                result.put((String) origin.get("id"), totalTime);

            } catch (Exception e) {
                System.err.println("[ODsayApiUtil] Summary API 호출 실패: " + e.getMessage());
            }
        }
        return result;
    }

    // 매물 클릭 시 상세 경로 조회용 - 길찾기 바 생성용
    public Map<String, Object> getTransitDetail(
            double startLng, double startLat, double destLng, double destLat
    ) {
        String url = String.format(
                "https://api.odsay.com/v1/api/searchPubTransPathT?SX=%f&SY=%f&EX=%f&EY=%f"
                        + "&searchType=1"     // 도착 시간 기준 검색
                        + "&endTime=0900"     // 오전 9시 기준
                        + "&apiKey=%s",
                startLng, startLat, destLng, destLat, odsayApiKey
        );

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null) {
                System.err.println("[ODsayApiUtil] 응답 body가 null입니다.");
                return Collections.emptyMap();
            }

            if (!body.containsKey("result")) {
                System.err.println("[ODsayApiUtil] 'result' 키가 없습니다.");
                return Collections.emptyMap();
            }

            Map<String, Object> resultObj = (Map<String, Object>) body.get("result");
            Object pathObj = resultObj.get("path");

            if (!(pathObj instanceof List)) {
                System.err.println("[ODsayApiUtil] path가 List가 아닙니다. 실제 타입: " +
                        (pathObj == null ? "null" : pathObj.getClass().getSimpleName()));
                return Collections.emptyMap();
            }

            List<Map<String, Object>> paths = (List<Map<String, Object>>) pathObj;
            if (paths.isEmpty()) {
                System.err.println("[ODsayApiUtil] path가 비어 있습니다.");
                return Collections.emptyMap();
            }

            System.out.println("[ODsayApiUtil] path 개수: " + paths.size());

            // 여기서 최소 소요시간 경로 하나만 선택
            Map<String, Object> bestPath = paths.stream()
                    .min(Comparator.comparingInt(p -> {
                        Object infoObj = p.get("info");
                        if (!(infoObj instanceof Map)) return Integer.MAX_VALUE;
                        Map<String, Object> info = (Map<String, Object>) infoObj;
                        Object totalTimeObj = info.get("totalTime");
                        if (!(totalTimeObj instanceof Number)) return Integer.MAX_VALUE;
                        return ((Number) totalTimeObj).intValue();
                    }))
                    .orElse(paths.get(0));  // 이상할 경우 인덱스 0 사용

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("path", Collections.singletonList(bestPath));

            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("result", resultMap);
            return wrapper;

        } catch (Exception e) {
            System.err.println("[ODsayApiUtil] Detail API 호출 실패: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }
    // ODsay API - 단일 출발지 API
    public Integer getTransitSingle(double startLat, double startLng, double destLat, double destLng) {

        String url = "https://api.odsay.com/v1/api/searchPubTransPathT?"
                + "SX=" + startLng
                + "&SY=" + startLat
                + "&EX=" + destLng
                + "&EY=" + destLat
                + "&apiKey=" + odsayApiKey;

        try {
            String json = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(json);

            if (!root.has("result")) return null;   // result 없을 시 경로 없음 처리

            JsonNode paths = root.get("result").get("path");
            if (paths == null || !paths.isArray() || paths.size() == 0) return null;

            int minTime = Integer.MAX_VALUE;

            for (JsonNode path : paths) {
                int time = path.get("info").get("totalTime").asInt();
                minTime = Math.min(minTime, time);
            }

            return minTime;

        } catch (Exception e) {
            System.err.println("[ODsayApiUtil] getTransitSingle 실패: " + e.getMessage());
            return null;
        }
    }
}