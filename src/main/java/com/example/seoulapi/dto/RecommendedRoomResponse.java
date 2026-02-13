package com.example.seoulapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 추천 매물 응답 dto - 매물 정보 + 통근 거리/시간 반환
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedRoomResponse {
    private Long id;

    // 주소 정보
    private String gu;
    private String dong;
    private String lotNumber;
    private String building;
    private String addressFull;

    // 계약 정보
    private String contractDate;
    private String rentType;
    private double deposit;
    private double rentFee;
    private Double area;
    private Integer floor;
    private Integer archYear;
    private Integer receiptYear;

    // 좌표
    private Double lat;
    private Double lng;

    // 통근 정보
    private double distanceKm;   // 거리 - 매물부터 목적지까지 수직거리
    private int durationMin;     // 소요시간
    private String method; // TRANSIT or WALK_FALLBACK or DRIVING

    // 이미지
    private String image;   // 이미지 url
}
