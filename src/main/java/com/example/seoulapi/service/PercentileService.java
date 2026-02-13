package com.example.seoulapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PercentileService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PYTHON_SCRIPT_PATH =
            new File("src/main/resources/ai_module/compute_percentile.py").getAbsolutePath();

    // 사용자가 선택한 법정동에 대해 선택된 항목의 백분위 정보 계산
    public Map<String, Object> computePercentile(String gu, String dong, Map<String, Integer> weights) {
        try {
            String weightsJson = objectMapper.writeValueAsString(weights);

            ProcessBuilder pb = new ProcessBuilder(
                    "python3", PYTHON_SCRIPT_PATH, weightsJson, gu, dong
            );
            pb.directory(new File("src/main/resources/ai_module"));
            Process process = pb.start();

            // 파이썬 에러 디버깅 용 stderr
            new Thread(() -> {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    err.lines().forEach(line -> System.err.println("[PYTHON ERR] " + line));
                } catch (IOException ignored) {}
            }).start();

            // stdout 읽기
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.lines().collect(Collectors.joining());
            process.waitFor();

            if (output.isBlank()) {
                throw new RuntimeException("Python 스크립트 결과 수신 오류");
            }

            return objectMapper.readValue(output, new TypeReference<>() {});

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("백분위 계산 중 오류 발생: " + e.getMessage());
        }
    }
}