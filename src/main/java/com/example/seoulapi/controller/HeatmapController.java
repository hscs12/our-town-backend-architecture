package com.example.seoulapi.controller;

import com.example.seoulapi.dto.HeatmapRequestDto;
import com.example.seoulapi.dto.HeatmapResponseDto;
import com.example.seoulapi.service.HeatmapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/heatmap")
@RequiredArgsConstructor
public class HeatmapController {

    private final HeatmapService heatmapService;

    @PostMapping
    public ResponseEntity<HeatmapResponseDto> getHeatmap(@RequestBody HeatmapRequestDto request) {
        HeatmapResponseDto result = heatmapService.getHeatmapData(
                request.getGu(),
                request.getWeights().getSafety(),
                request.getWeights().getLiving(),
                request.getWeights().getGreen(),
                request.getWeights().getTransport(),
                request.getWeights().getMedical(),
                request.getWeights().getConvenience()
        );
        return ResponseEntity.ok(result);
    }
}