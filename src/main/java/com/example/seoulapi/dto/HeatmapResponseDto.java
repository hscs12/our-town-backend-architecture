package com.example.seoulapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapResponseDto {
    private String gu;  //선택한 구 이름
    private int count;   // 법정동 개수
    private List<Map<String, Object>> heatmap;
    private List<Map<String, Object>> ranking; // 점수순 정렬 리스트
}
