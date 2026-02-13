package com.example.seoulapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecommendedRoomsPageResponse {
    private int cursor;          // 이번 요청 시작점
    private int batchSize;       // 이번에 계산한 후보 수
    private boolean hasNext;     // 다음 배치가 남아있는지
    private int nextCursor;      // 다음 요청에 넘길 cursor
    private long totalCandidates; // 예산/전월세 등 DB필터 통과 후보 수
    private List<RecommendedRoomResponse> items; // 이번 배치에서 조건 만족한 추천들
}