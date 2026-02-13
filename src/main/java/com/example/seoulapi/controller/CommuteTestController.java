/*package com.example.seoulapi.controller;

import com.example.seoulapi.service.CommuteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/commute")
public class CommuteTestController {

    private final CommuteService commuteService;

    @PostMapping("/paged")
    public Map<String, Object> commutePaged(
            @RequestParam String transport,           // DRIVING or TRANSIT
            @RequestParam double destLng,
            @RequestParam double destLat,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestBody List<Map<String, Object>> origins
    ) {
        if ("DRIVING".equalsIgnoreCase(transport)) {
            return commuteService.calculateDrivingInfoPaged(origins, destLng, destLat, page, size);
        }
        if ("TRANSIT".equalsIgnoreCase(transport)) {
            return commuteService.calculateTransitSummaryPaged(origins, destLng, destLat, page, size);
        }
        return Map.of("error", "transport must be DRIVING or TRANSIT");
    }
}*/