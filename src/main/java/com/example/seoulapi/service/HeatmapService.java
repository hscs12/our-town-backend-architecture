package com.example.seoulapi.service;

import com.example.seoulapi.dto.HeatmapResponseDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HeatmapService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PercentileService percentileService;  //PercentileService 주입

    private static final String SCORE_SCRIPT_PATH =
            new File("src/main/resources/ai_module/score_model.py").getAbsolutePath();

    public HeatmapResponseDto getHeatmapData(
            String gu,
            double safety,
            double living,
            double green,
            double transport,
            double medical,
            double convenience
    ) {
        try {
            // Python 인자 구성
            Map<String, Integer> weights = new LinkedHashMap<>();
            weights.put("안전", (int) safety);
            weights.put("주거환경", (int) living);
            weights.put("환경및녹지", (int) green);
            weights.put("교통", (int) transport);
            weights.put("의료", (int) medical);
            weights.put("생활편의", (int) convenience);

            String weightsJson = objectMapper.writeValueAsString(weights);

            // Python 점수 계산
            ProcessBuilder pb = new ProcessBuilder("python3", SCORE_SCRIPT_PATH, weightsJson, gu);
            pb.directory(new File("src/main/resources/ai_module"));
            Process process = pb.start();

            // stderr 로그 출력
            new Thread(() -> {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    err.lines().forEach(line -> System.err.println("[PYTHON ERR] " + line));
                } catch (IOException ignored) {}
            }).start();

            // stdout(JSON) 읽기
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.lines().collect(Collectors.joining());
            process.waitFor();

            if (output.isBlank()) {
                throw new RuntimeException("Python 스크립트 결과 수신 오류");
            }

            // JSON 파싱
            List<Map<String, Object>> resultList = objectMapper.readValue(output, new TypeReference<>() {});

            resultList.sort((a, b) -> Double.compare(
                    ((Number) b.get("최종점수")).doubleValue(),
                    ((Number) a.get("최종점수")).doubleValue()
            ));

            Map<String, Integer> engWeights = new LinkedHashMap<>();
            engWeights.put("safety", (int) safety);
            engWeights.put("living", (int) living);
            engWeights.put("green", (int) green);
            engWeights.put("transport", (int) transport);
            engWeights.put("medical", (int) medical);
            engWeights.put("convenience", (int) convenience);

            // 각 법정동마다 percentile 추가
            for (Map<String, Object> dongItem : resultList) {
                String dongName = dongItem.get("법정동").toString();

                // AI 모듈 호출
                Map<String, Object> percentileMap = percentileService.computePercentile(
                        gu,
                        dongName,
                        engWeights
                );

                dongItem.put("percentiles", percentileMap);
            }

            // 순위
            List<Map<String, Object>> rankedList = new ArrayList<>();
            for (int i = 0; i < resultList.size(); i++) {
                Map<String, Object> item = resultList.get(i);
                rankedList.add(Map.of(
                        "rank", i + 1,
                        "법정동코드", item.get("법정동코드"),
                        "법정동", item.get("법정동"),
                        "시군구명", item.get("시군구명"),
                        "최종점수", item.get("최종점수")
                ));
            }

            return HeatmapResponseDto.builder()
                    .gu(gu)
                    .count(resultList.size())
                    .heatmap(resultList)
                    .ranking(rankedList)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("AI 점수 계산 중 오류 발생: " + e.getMessage());
        }
    }
}


