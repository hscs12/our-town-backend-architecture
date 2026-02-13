package com.example.seoulapi.controller;

import com.example.seoulapi.service.CommuteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/commute")
public class CommuteController {

    private final CommuteService commuteService;

    // 대중교통 길찾기 상세 조회 API
    @GetMapping("/transit-detail")
    public Map<String, Object> getTransitDetail(
            @RequestParam double startLat,
            @RequestParam double startLng,
            @RequestParam double destLat,
            @RequestParam double destLng,
            @RequestParam String method
    ) {
        String normalizedMethod = method.trim().toUpperCase();
        return commuteService.getTransitDetail(startLat, startLng, destLat, destLng, normalizedMethod);
    }
}