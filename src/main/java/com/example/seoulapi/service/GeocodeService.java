package com.example.seoulapi.service;

import com.example.seoulapi.model.RoomEntity;
import com.example.seoulapi.repository.RoomRepository;
import com.example.seoulapi.util.KakaoApiUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;


// Pending 상태 매물 1건 가져와 카카오 API 호출하여 좌표 변환
@Service
@RequiredArgsConstructor
public class GeocodeService {

    private final RoomRepository roomRepository;
    private final KakaoApiUtil kakaoApiUtil;

    @Transactional
    public void processOnePendingRoom() {
        // PENDING 상태 중 attempts < 3 인 매물 하나 가져오기
        Optional<RoomEntity> optionalRoom = roomRepository
                .findTopByGeocodeStatusAndGeocodeAttemptsLessThanOrderByIdAsc("PENDING", 3);

        if (optionalRoom.isEmpty()) {
            return; // 처리할 것 x
        }

        RoomEntity room = optionalRoom.get();

        try {
            // 카카오 API 호출
            double[] coords = kakaoApiUtil.getCoordinatesByAddress(room.getAddressFull());

            if (coords != null) {
                // 성공 시 좌표&상태 업데이트
                room.setX(coords[0]);
                room.setY(coords[1]);
                room.setGeocodeStatus("SUCCESS");
                room.setGeocodeSource("kakao");
                room.setGeocodeUpdatedAt(LocalDateTime.now());
            } else {
                // 실패 시 시도 횟수만 증가
                room.setGeocodeAttempts(room.getGeocodeAttempts() + 1);
            }

        } catch (Exception e) {
            // API 호출 중 오류시 시도 횟수 증가
            room.setGeocodeAttempts(room.getGeocodeAttempts() + 1);
        }

        // 저장
        roomRepository.save(room);
    }
}