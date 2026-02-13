package com.example.seoulapi.model;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// 매물
@Entity
@Table(name = "rooms")
@Getter
@Setter
public class RoomEntity {

    @Id
    @SequenceGenerator(
            name = "room_seq_gen",
            sequenceName = "room_seq",
            allocationSize = 1000
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "room_seq_gen")
    private Long id;    // pk

    private String gu;          // 구
    private String dong;        // 동
    private String lotNumber;   // 지번 (본번-부번)
    private String building;    // 단지명
    private String addressFull; // 최종 주소 문자열 (지번 + 단지명)
    private String contractDate;  // 계약일
    private String rentType;      // 전월세 구분
    private Double deposit;       // 보증금
    private Double rentFee;       // 월세
    private Double area;          // 전용면적
    private Integer floor;            // 층
    private Integer archYear;         // 건축년도
    private Integer receiptYear;      // 접수연도

    private String image;

    // 좌표
    @Column(nullable = true)
    private Double x; // 경도
    @Column(nullable = true)
    private Double y; // 위도

    // 서울시 계약을 유일하게 식별하는 ID (구+동+주소+건물명+계약일자)
    @Column(unique = true)
    private String apiId;

    // 지오코딩 관리용 컬럼
    @Column(name = "geocode_status", length = 20)
    private String geocodeStatus;      // PENDING or SUCCESS or FAILED or FALLBACK

    @Column(name = "geocode_attempts")
    private Integer geocodeAttempts;

    @Column(name = "geocode_source", length = 20)
    private String geocodeSource;      // kakao

    @Column(name = "geocode_updated_at")
    private LocalDateTime geocodeUpdatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoomEntity that = (RoomEntity) o;
        return Objects.equals(apiId, that.apiId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiId);
    }

    // 기본값 세팅
    @PrePersist
    public void prePersist() {
        if (this.geocodeStatus == null) this.geocodeStatus = "PENDING";
        if (this.geocodeAttempts == null) this.geocodeAttempts = 0;
    }
}
