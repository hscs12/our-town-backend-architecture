package com.example.seoulapi.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

// Kakao API 유틸
@Component
public class KakaoApiUtil {

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    // 주소 -> 좌표
    public double[] getCoordinatesByAddress(String address) {
        return fetchCoordinates("https://dapi.kakao.com/v2/local/search/address.json", address);
    }

    // 공통 호출 로직
    private double[] fetchCoordinates(String baseUrl, String query) {
        try {
            String apiUrl = baseUrl + "?query=" + URLEncoder.encode(query, "UTF-8");
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "KakaoAK " + kakaoApiKey);

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(sb.toString());
            JsonNode docs = root.path("documents");

            if (docs.isArray() && docs.size() > 0) {
                JsonNode first = docs.get(0);
                double x = first.path("x").asDouble(); // 경도
                double y = first.path("y").asDouble(); // 위도
                return new double[]{x, y};
            } else {
                System.out.println("[KakaoApiUtil] 검색 결과 없음: " + query);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 다중 출발지 길찾기 - 각 매물 id별 거리(m), 시간(초) 반환
    public Map<String, Map<String, Integer>> getDrivingTimes(
            List<Map<String, Object>> origins, double destLng, double destLat
    ) {
        String url = "https://apis-navi.kakaomobility.com/v1/origins/directions";

        List<Map<String, Object>> updatedOrigins = new ArrayList<>();
        for (Map<String, Object> o : origins) {
            Map<String, Object> origin = new HashMap<>();
            origin.put("x", o.get("lng"));
            origin.put("y", o.get("lat"));
            origin.put("radius", 10000);
            origin.put("key", o.get("id"));
            updatedOrigins.add(origin);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("origins", updatedOrigins);
        body.put("destination", Map.of("x", destLng, "y", destLat));
        body.put("radius", 10000); // 반경 10km

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            System.out.println("[DEBUG] KakaoMobility Response: " + responseBody);


            if (responseBody == null || !responseBody.containsKey("routes"))
                return Collections.emptyMap();

            List<Map<String, Object>> routes = (List<Map<String, Object>>) responseBody.get("routes");
            Map<String, Map<String, Integer>> commuteMap = new HashMap<>();

            for (Map<String, Object> route : routes) {
                String key = (String) route.get("key");

                if (!route.containsKey("summary") || route.get("summary") == null) {
                    System.out.println("[KakaoApiUtil] summary 없음 (key=" + key + ")");
                    continue;
                }

                Map<String, Object> summary = (Map<String, Object>) route.get("summary");

                Number distanceNum = (Number) summary.get("distance");
                Number durationNum = (Number) summary.get("duration");

                int distance = distanceNum.intValue();
                int duration = durationNum.intValue();

                commuteMap.put(key, Map.of(
                        "distance", distance,
                        "duration", duration
                ));
            }

            return commuteMap;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    // 단일 출발지 길찾기
    public Map<String, Integer> getDrivingSingle(double startLng, double startLat,
                                                 double destLng, double destLat) {

        try {
            String url = String.format(
                    "https://apis-navi.kakaomobility.com/v1/directions?origin=%f,%f&destination=%f,%f",
                    startLng, startLat, destLng, destLat
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map.class
            );

            Map<String, Object> resp = response.getBody();
            if (resp == null || !resp.containsKey("routes")) return null;

            List<Map<String, Object>> routes = (List<Map<String, Object>>) resp.get("routes");
            if (routes.isEmpty()) return null;

            Map<String, Object> summary = (Map<String, Object>) routes.get(0).get("summary");

            int distance = ((Number) summary.get("distance")).intValue();
            int duration = ((Number) summary.get("duration")).intValue();

            return Map.of(
                    "distance", distance,
                    "duration", duration
            );

        } catch (Exception e) {
            System.err.println("[KakaoApiUtil] 단일 경로 호출 실패: " + e.getMessage());
            return null;
        }
    }
}
