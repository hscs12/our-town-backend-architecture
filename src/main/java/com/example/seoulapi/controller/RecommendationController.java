package com.example.seoulapi.controller;

import com.example.seoulapi.dto.RecommendationRequestDto;
import com.example.seoulapi.dto.RecommendedRoomResponse;
import com.example.seoulapi.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 추천 매물 API 컨트롤러 - 통근 시간/거리 기반으로 추천 리스트 반환
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendation")
public class RecommendationController {

    private final RecommendationService recommendationService;

    // 한 번에 계산
    @PostMapping
    public ResponseEntity<List<RecommendedRoomResponse>> getRecommendedRooms(
            @RequestBody RecommendationRequestDto request
    ) {
        List<RecommendedRoomResponse> results =
                recommendationService.getRecommendedRooms(request);
        return ResponseEntity.ok(results);
    }

    // 개선 - 배치(페이지네이션) 계산
    @PostMapping("/paged")
    public ResponseEntity<Map<String, Object>> getRecommendedRoomsPaged(
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "10") int batchSize,
            @RequestBody RecommendationRequestDto request
    ) {
        Map<String, Object> result =
                recommendationService.getRecommendedRoomsPaged(request, cursor, batchSize);
        return ResponseEntity.ok(result);
    }
}