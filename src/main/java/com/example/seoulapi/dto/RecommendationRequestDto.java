package com.example.seoulapi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecommendationRequestDto {
    private String city;              // 서울특별시
    private String gu;          // 구
    private String dong;          // 동

    private Integer deposit;          // 보증금
    private Integer rentFee;          // 월세
    private String rentType;          // 전세 or 월세

    private WorkplaceCoords workplaceCoords; // 직장 or 학교 좌표
    private Integer commuteTimeLimit; // 통근 시간 범위
    private String transportType;     // 자차 or 대중교통

    @Getter
    @Setter
    public static class WorkplaceCoords {
        private Double lat;
        private Double lng;
    }
}