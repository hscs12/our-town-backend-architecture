package com.example.seoulapi.repository;

import com.example.seoulapi.model.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// 매물 DB 접근
@Repository
public interface RoomRepository extends JpaRepository<RoomEntity, Long> {

    List<RoomEntity> findByGu(String gu);

    List<RoomEntity> findByDong(String dong);

    List<RoomEntity> findByGuAndDong(String gu, String dong);

    List<RoomEntity> findByDepositBetween(int min, int max);

    List<RoomEntity> findByRentFeeBetween(int min, int max);

    boolean existsByApiId(String apiId);


    // 모든 apiId만 가져오기(중복 체크용)
    @Query("select r.apiId from RoomEntity r")
    List<String> findAllApiIds();

    List<RoomEntity> findTop1000ByGeocodeStatus(String geocodeStatus);

    Optional<RoomEntity> findTopByGeocodeStatusAndGeocodeAttemptsLessThanOrderByIdAsc(String status, int maxAttempts);

    // 좌표값이 비어있거나 0인 매물 조회
    @Query("SELECT r FROM RoomEntity r WHERE (r.x IS NULL OR r.x = 0) OR (r.y IS NULL OR r.y = 0)")
    List<RoomEntity> findRoomsWithoutCoordinates();
}
