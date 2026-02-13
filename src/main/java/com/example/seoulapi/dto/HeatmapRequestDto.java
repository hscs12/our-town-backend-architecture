package com.example.seoulapi.dto;

import lombok.Data;

@Data
public class HeatmapRequestDto {

    private String gu; // 사용자가 선택한 구 이름

    private Weights weights; // 사용자가 설정한 가중치 묶음

    @Data
    public static class Weights {
        private int safety;
        private int living;
        private int green;
        private int transport;
        private int medical;
        private int convenience;
    }
}