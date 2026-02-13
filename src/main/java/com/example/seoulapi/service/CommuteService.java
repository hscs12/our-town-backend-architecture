package com.example.seoulapi.service;

import com.example.seoulapi.util.KakaoApiUtil;
import com.example.seoulapi.util.ODsayApiUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommuteService {

    private final KakaoApiUtil kakaoApiUtil;
    private final ODsayApiUtil odsayApiUtil;

    // 자차 - 단일 경로 반복 호출 방식
    public List<Map<String, Object>> calculateDrivingInfo(
            List<Map<String, Object>> origins, double destLng, double destLat
    ) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> origin : origins) {

            String id = (String) origin.get("id");
            double lat = (double) origin.get("lat");
            double lng = (double) origin.get("lng");

            // 단일 출발지 경로 API 호출
            Map<String, Integer> info =
                    kakaoApiUtil.getDrivingSingle(lng, lat, destLng, destLat);

            // 경로 없으면 스킵
            if (info == null) continue;

            int durationMin = info.get("duration") / 60;

            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("duration_min", durationMin);
            map.put("method", "DRIVING");

            results.add(map);
        }

        return results;
    }

    // 대중교통 요약
    public List<Map<String, Object>> calculateTransitSummary(
            List<Map<String, Object>> origins, double destLng, double destLat
    ) {

        ExecutorService executor = Executors.newFixedThreadPool(20);

        List<CompletableFuture<Map<String, Object>>> futures =
                origins.stream()
                        .map(origin -> CompletableFuture.supplyAsync(() -> {

                                            String id = (String) origin.get("id");
                                            double lat = (double) origin.get("lat");
                                            double lng = (double) origin.get("lng");

                                            try {
                                                Integer transitTime = odsayApiUtil.getTransitSingle(
                                                        lat, lng, destLat, destLng
                                                );

                                                if (transitTime != null) {
                                                    Map<String, Object> info = new HashMap<>();
                                                    info.put("id", id);
                                                    info.put("duration_min", transitTime);
                                                    info.put("method", "TRANSIT");
                                                    return info;
                                                }

                                            } catch (Exception ignored) {}

                                            // 1. fallback 수행 전 직선거리 계산
                                            double straightKm = getDistance(lat, lng, destLat, destLng);

                                            // 2. 직선거리 > 1.5km 인 경우 도보 불가 -> 통근 불가 처리
                                            if (straightKm > 1.5) {
                                                Map<String, Object> fail = new HashMap<>();
                                                fail.put("id", id);
                                                fail.put("duration_min", Integer.MAX_VALUE); // 필터링에서 제외
                                                fail.put("method", "NO_PATH");
                                                return fail;
                                            }

                                            // 3. 1.5km 이하인 경우 WALK fallback 실행
                                            Map<String, Object> fallback = new HashMap<>();
                                            fallback.put("id", id);
                                            fallback.put("duration_min", drivingToWalkingFallback(lat, lng, destLat, destLng));
                                            fallback.put("method", "WALK_FALLBACK");
                                            return fallback;


                                        }, executor)
                                        .completeOnTimeout(
                                                fallbackMap(origin, destLat, destLng),
                                                1200,
                                                TimeUnit.MILLISECONDS
                                        )
                        )
                        .collect(Collectors.toList());

        List<Map<String, Object>> result =
                futures.stream().map(CompletableFuture::join).collect(Collectors.toList());

        executor.shutdown();
        return result;
    }

    // 도보 fallback 처리
    private int drivingToWalkingFallback(double lat, double lng, double destLat, double destLng) {

        Map<String, Object> origin = Map.of(
                "id", "fallback",
                "lat", lat,
                "lng", lng
        );

        // 1. 카카오 자차 API 호출해 거리 받기
        Map<String, Map<String, Integer>> data =
                kakaoApiUtil.getDrivingTimes(List.of(origin), destLng, destLat);

        if (data == null || data.isEmpty()) {
            // 거리 가져오지 못하는 경우엔 직선거리로 처리(최악의 경우)
            double straight = getDistance(lat, lng, destLat, destLng) * 1000; // m
            return (int) Math.min(straight / 80.0, 20);
        }

        int distanceMeters = data.get("fallback").get("distance");  // m

        // 2. 도보 속도 기준 계산 (80m/min)
        double walking = distanceMeters / 80.0;

        // 20분을 넘으면 도보 불가 처리
        if (walking > 20) {
            return Integer.MAX_VALUE;
        }

        return (int) Math.round(walking);
    }

    private Map<String, Object> fallbackMap(Map<String, Object> origin, double destLat, double destLng) {
        String id = (String) origin.get("id");
        double lat = (double) origin.get("lat");
        double lng = (double) origin.get("lng");

        double straightKm = getDistance(lat, lng, destLat, destLng);

        if (straightKm > 1.5) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("id", id);
            fail.put("duration_min", Integer.MAX_VALUE);
            fail.put("method", "NO_PATH");
            return fail;
        }

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("id", id);
        fallback.put("duration_min", drivingToWalkingFallback(lat, lng, destLat, destLng));
        fallback.put("method", "WALK_FALLBACK");
        return fallback;
    }



    // 대중교통 상세
    public Map<String, Object> getTransitDetail(
            double startLat,
            double startLng,
            double destLat,
            double destLng,
            String method
    ) {
        System.out.println("[CommuteService] method = " + method);
        // 0. 애초에 추천 단게에서 WALK_FALLBACK였던 매물인 경우 → ODsay 다시 안 부르고, "도보 OO분 이내 + 거리" 내려줌
        if ("WALK_FALLBACK".equals(method)) {
            int walkingMin = drivingToWalkingFallback(startLat, startLng, destLat, destLng);

            // 거리 계산
            Map<String, Object> origin = Map.of(
                    "id", "detail",
                    "lat", startLat,
                    "lng", startLng
            );
            Map<String, Map<String, Integer>> driving =
                    kakaoApiUtil.getDrivingTimes(List.of(origin), destLng, destLat);

            int distanceMeters;
            if (driving != null && driving.containsKey("detail")) {
                distanceMeters = driving.get("detail").get("distance");
            } else {
                distanceMeters = (int) Math.round(
                        getDistance(startLat, startLng, destLat, destLng) * 1000
                );
            }

            Map<String, Object> walkInfo = new HashMap<>();
            walkInfo.put("trafficType", 3);           // 도보
            walkInfo.put("totalTime", walkingMin);    // 도보 소요시간
            walkInfo.put("totalTimeSec", walkingMin * 60);
            walkInfo.put("distance", distanceMeters); // 총 거리

            return walkInfo;
        }

        // 1. 대중교통 상세 조회 시도
        Map<String, Object> response =
                odsayApiUtil.getTransitDetail(startLng, startLat, destLng, destLat);

        if (response != null && response.containsKey("result")) {
            return response;  // 기존 대중교통 상세 경로 반환
        }

        // ODsay가 경로를 못 준 경우 - 거리 기준으로 도보 허용 여부 체크
        double straightKm = getDistance(startLat, startLng, destLat, destLng);

        // 1.5km 초과시 도보 fallback도 허용하지 않고 경로 없음 처리
        if (straightKm > 1.5) {
            Map<String, Object> noPath = new HashMap<>();
            noPath.put("trafficType", -1);
            noPath.put("message", "NO_PATH");   // 원인 확인
            return noPath;
        }

        // 2. ODsay 상세 경로가 없다면 → 도보로 처리
        int walkingMin = drivingToWalkingFallback(startLat, startLng, destLat, destLng);

        // 거리 계산
        Map<String, Object> origin = Map.of(
                "id", "detail",
                "lat", startLat,
                "lng", startLng
        );
        Map<String, Map<String, Integer>> driving =
                kakaoApiUtil.getDrivingTimes(List.of(origin), destLng, destLat);

        int distanceMeters;
        if (driving != null && driving.containsKey("detail")) {
            distanceMeters = driving.get("detail").get("distance");
        } else {
            distanceMeters = (int) Math.round(getDistance(startLat, startLng, destLat, destLng) * 1000);
        }

        // 3. 프론트에 줄 도보 상세 정보
        Map<String, Object> walkInfo = new HashMap<>();
        walkInfo.put("trafficType", 3);    // 3 : 도보
        walkInfo.put("totalTime", walkingMin);
        walkInfo.put("totalTimeSec", walkingMin * 60);
        walkInfo.put("distance", distanceMeters);

        return walkInfo;
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