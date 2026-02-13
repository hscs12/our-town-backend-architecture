package com.example.seoulapi.service;

import com.example.seoulapi.dto.RecommendationRequestDto;
import com.example.seoulapi.dto.RecommendedRoomResponse;
import com.example.seoulapi.model.RoomEntity;
import com.example.seoulapi.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RoomRepository roomRepository;
    private final CommuteService commuteService;

    public List<RecommendedRoomResponse> getRecommendedRooms(RecommendationRequestDto request) {

        String gu = request.getGu();
        String dong = request.getDong();
        Integer deposit = request.getDeposit();
        Integer monthly = request.getRentFee();
        String rentType = request.getRentType();
        String transport = request.getTransportType();
        Integer commuteLimit = request.getCommuteTimeLimit();

        double destLat = request.getWorkplaceCoords().getLat();
        double destLng = request.getWorkplaceCoords().getLng();

        // 1. 구/동 매물 필터
        List<RoomEntity> rooms = roomRepository.findByGuAndDong(gu, dong);
        System.out.println("1. DB 매물 수: " + rooms.size());

        // 2. 전세/월세 예산 필터
        List<RoomEntity> filtered = rooms.stream()
                .filter(r -> {
                    if ("전세".equals(rentType)) {
                        return "전세".equals(r.getRentType()) && r.getDeposit() <= deposit;
                    }
                    if ("월세".equals(rentType)) {
                        boolean depositOk = r.getDeposit() <= deposit;
                        boolean rentOk = (monthly != null) ? r.getRentFee() <= monthly : true;
                        return "월세".equals(r.getRentType()) && depositOk && rentOk;
                    }
                    return false;
                })
                .collect(Collectors.toList());
        System.out.println("2. 예산 필터 후: " + filtered.size());

        // 3. 동적 거리 프리필터 -> 단일 목적지 API로 변경하면서 필요 없을 듯?
        /*
        double dynamicMaxDist = Math.min(Math.max(commuteLimit * 0.33, 3.0), 10.0);
        filtered = filtered.stream()
                .filter(r -> getDistance(r.getY(), r.getX(), destLat, destLng) <= dynamicMaxDist)
                .collect(Collectors.toList());
        System.out.println("3. 거리 프리필터 후: " + filtered.size());
         */

        // 3. 가까운 순으로 최대 150개
        List<RoomEntity> selected = filtered.stream()
                .sorted(Comparator.comparingDouble(
                        r -> getDistance(r.getY(), r.getX(), destLat, destLng)))
                .limit("자차".equalsIgnoreCase(transport) ? 30 : 150) // 자차의 경우 상위 30개만
                .collect(Collectors.toList());
        System.out.println("4. limit(150) 적용 후: " + selected.size());

        // 4. 출발지 리스트 생성
        List<Map<String, Object>> origins = selected.stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", String.valueOf(r.getId()));
                    m.put("lat", r.getY());
                    m.put("lng", r.getX());
                    return m;
                })
                .collect(Collectors.toList());

        // 5. 통근시간 계산
        List<Map<String, Object>> commuteInfos;
        if ("자차".equalsIgnoreCase(transport)) {
            commuteInfos = commuteService.calculateDrivingInfo(origins, destLng, destLat);
        } else {
            commuteInfos = commuteService.calculateTransitSummary(origins, destLng, destLat);
        }

        // 6. duration_min은 항상 존재하므로 필터 안정적
        List<Map<String, Object>> filteredInfos =
                commuteInfos.stream()
                        .filter(info ->
                                (int) info.get("duration_min") <= commuteLimit)
                        .collect(Collectors.toList());

        // 7. DTO 변환
        List<RecommendedRoomResponse> finalList = filteredInfos.stream()
                .map(info -> {

                    String id = (String) info.get("id");
                    RoomEntity room = selected.stream()
                            .filter(r -> String.valueOf(r.getId()).equals(id))
                            .findFirst().orElse(null);

                    if (room == null) return null;

                    int durationMin = (int) info.get("duration_min");
                    String method = (String) info.get("method");

                    double distanceKm = getDistance(room.getY(), room.getX(), destLat, destLng);

                    return RecommendedRoomResponse.builder()
                            .id(room.getId())
                            .gu(room.getGu())
                            .dong(room.getDong())
                            .lotNumber(room.getLotNumber())
                            .building(room.getBuilding())
                            .addressFull(room.getAddressFull())
                            .lat(room.getY())
                            .lng(room.getX())
                            .contractDate(room.getContractDate())
                            .rentType(room.getRentType())
                            .deposit(room.getDeposit())
                            .rentFee(room.getRentFee())
                            .area(room.getArea())
                            .floor(room.getFloor())
                            .archYear(room.getArchYear())
                            .receiptYear(room.getReceiptYear())
                            .distanceKm(distanceKm)
                            .durationMin(durationMin)
                            .method(method)
                            .image(room.getImage())
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RecommendedRoomResponse::getDurationMin))
                .collect(Collectors.toList());

        System.out.println("5. 최종 출력 매물 수: " + finalList.size());
        return finalList;
    }

    // 개선 - 계산 페이지네이션 버전
    public Map<String, Object> getRecommendedRoomsPaged(
            RecommendationRequestDto request,
            int cursor,
            int batchSize
    ) {
        String gu = request.getGu();
        String dong = request.getDong();
        Integer deposit = request.getDeposit();
        Integer monthly = request.getRentFee();
        String rentType = request.getRentType();
        String transport = request.getTransportType();
        Integer commuteLimit = request.getCommuteTimeLimit();

        double destLat = request.getWorkplaceCoords().getLat();
        double destLng = request.getWorkplaceCoords().getLng();

        // 배치 크기 기본값
        if (batchSize <= 0) {
            batchSize = "자차".equalsIgnoreCase(transport) ? 30 : 30;
        }
        if (cursor < 0) cursor = 0;

        // 1. 구/동 전체 후보
        List<RoomEntity> rooms = roomRepository.findByGuAndDong(gu, dong);

        // 2. 예산 필터 (좌표 없는 건 제외)
        List<RoomEntity> filtered = rooms.stream()
                .filter(r -> r.getX() != null && r.getY() != null)
                .filter(r -> {
                    if ("전세".equals(rentType)) {
                        return "전세".equals(r.getRentType())
                                && r.getDeposit() != null
                                && deposit != null
                                && r.getDeposit() <= deposit;
                    }
                    if ("월세".equals(rentType)) {
                        boolean depositOk = r.getDeposit() != null && deposit != null && r.getDeposit() <= deposit;
                        boolean rentOk = (monthly != null)
                                ? (r.getRentFee() != null && r.getRentFee() <= monthly)
                                : true;
                        return "월세".equals(r.getRentType()) && depositOk && rentOk;
                    }
                    return false;
                })
                .collect(Collectors.toList());

        // 3. 가까운 순으로 정렬
        List<RoomEntity> candidates = filtered.stream()
                .sorted(Comparator.comparingDouble(r -> getDistance(r.getY(), r.getX(), destLat, destLng)))
                .collect(Collectors.toList());

        int totalCandidates = candidates.size();

        // 4. cursor~cursor+batchSize 배치 슬라이스
        int from = Math.min(cursor, totalCandidates);
        int to = Math.min(from + batchSize, totalCandidates);
        List<RoomEntity> batch = (from >= to) ? List.of() : candidates.subList(from, to);

        List<Long> computedIds = batch.stream()
                .map(RoomEntity::getId)
                .collect(Collectors.toList());

        // 5. 배치 origins 생성
        List<Map<String, Object>> origins = batch.stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", String.valueOf(r.getId()));
                    m.put("lat", r.getY());
                    m.put("lng", r.getX());
                    return m;
                })
                .collect(Collectors.toList());

        // 6. 외부 API 호출은 이 배치에 대해서만
        List<Map<String, Object>> commuteInfos;
        if ("자차".equalsIgnoreCase(transport)) {
            commuteInfos = commuteService.calculateDrivingInfo(origins, destLng, destLat);
        } else {
            commuteInfos = commuteService.calculateTransitSummary(origins, destLng, destLat);
        }

        // 7. 통근시간 조건 만족만 필터
        List<Map<String, Object>> passedInfos = commuteInfos.stream()
                .filter(info -> (int) info.get("duration_min") <= commuteLimit)
                .collect(Collectors.toList());

        // 8. 매칭 최적화
        Map<String, RoomEntity> roomMap = batch.stream()
                .collect(Collectors.toMap(r -> String.valueOf(r.getId()), r -> r));

        // 9. DTO 변환
        List<RecommendedRoomResponse> items = passedInfos.stream()
                .map(info -> {
                    String id = (String) info.get("id");
                    RoomEntity room = roomMap.get(id);
                    if (room == null) return null;

                    int durationMin = (int) info.get("duration_min");
                    String method = (String) info.get("method");
                    double distanceKm = getDistance(room.getY(), room.getX(), destLat, destLng);

                    return RecommendedRoomResponse.builder()
                            .id(room.getId())
                            .gu(room.getGu())
                            .dong(room.getDong())
                            .lotNumber(room.getLotNumber())
                            .building(room.getBuilding())
                            .addressFull(room.getAddressFull())
                            .lat(room.getY())
                            .lng(room.getX())
                            .contractDate(room.getContractDate())
                            .rentType(room.getRentType())
                            .deposit(room.getDeposit())
                            .rentFee(room.getRentFee())
                            .area(room.getArea())
                            .floor(room.getFloor())
                            .archYear(room.getArchYear())
                            .receiptYear(room.getReceiptYear())
                            .distanceKm(distanceKm)
                            .durationMin(durationMin)
                            .method(method)
                            .image(room.getImage())
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RecommendedRoomResponse::getDurationMin))
                .collect(Collectors.toList());

        // 10. pagination 메타
        boolean hasNext = to < totalCandidates;
        int nextCursor = to;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cursor", cursor);
        response.put("batchSize", batchSize);
        response.put("totalCandidates", totalCandidates);
        response.put("scannedFrom", from);
        response.put("scannedTo", to);

        response.put("computedCount", batch.size());
        response.put("computedIds", computedIds);

        response.put("returned", items.size());
        response.put("hasNext", hasNext);
        response.put("nextCursor", nextCursor);
        response.put("items", items);

        return response;
    }


    // 거리 계산
    private double getDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }
}