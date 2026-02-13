package com.example.seoulapi.service;

import com.example.seoulapi.util.KakaoApiUtil;
import com.example.seoulapi.model.RoomEntity;
import com.example.seoulapi.repository.RoomRepository;
import com.example.seoulapi.util.AddressUtil;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;


import java.util.*;

@Service
public class RentService {

    private final RoomRepository roomRepository;
    private final EntityManager entityManager;
    private final KakaoApiUtil kakaoApiUtil;


    public RentService(RoomRepository roomRepository, EntityManager entityManager, KakaoApiUtil kakaoApiUtil) {
        this.roomRepository = roomRepository;
        this.entityManager = entityManager;
        this.kakaoApiUtil = kakaoApiUtil;
    }

    public List<RoomEntity> getAllRooms() {
        return roomRepository.findAll();
    }

    public List<RoomEntity> getRoomsByGu(String gu) {
        return roomRepository.findByGu(gu);
    }

    public List<RoomEntity> getRoomsByDong(String dong) {
        return roomRepository.findByDong(dong);
    }

    public List<RoomEntity> getRoomsByDepositRange(int min, int max) {
        return roomRepository.findByDepositBetween(min, max);
    }

    public List<RoomEntity> getRoomsByRentFeeRange(int min, int max) {
        return roomRepository.findByRentFeeBetween(min, max);
    }

    @Transactional
    public void fixCoordinatesSequentially() {
        List<RoomEntity> rooms = roomRepository.findRoomsWithoutCoordinates();
        System.out.println("[좌표 미보정 매물 수]: " + rooms.size());

        int updatedCount = 0;

        for (RoomEntity room : rooms) {
            try {
                String cleanAddress = AddressUtil.clean(room.getAddressFull());
                double[] coords = kakaoApiUtil.getCoordinatesByAddress(cleanAddress);


                if (coords != null) {
                    room.setX(coords[0]);
                    room.setY(coords[1]);
                    room.setGeocodeStatus("SUCCESS");
                } else {
                    room.setGeocodeStatus("FAILED");
                }

                room.setGeocodeAttempts(room.getGeocodeAttempts() + 1);

                updatedCount++;

                // 100건마다 flush
                if (updatedCount % 500 == 0) {
                    roomRepository.flush();

                }

                // API 한도 초과 방지용
                Thread.sleep(100);

            } catch (Exception e) {
                System.err.println("좌표 변환 실패: " + room.getAddressFull());
                e.printStackTrace();
            }
        }

        roomRepository.flush();
    }


    // 단건 저장
    public void saveRoom(JsonNode row) {
        RoomEntity entity = convertRowToEntity(row, new HashSet<>());
        if (entity != null) {
            roomRepository.save(entity);
        }
    }

    // 여러 건 저장 (배치 삽입 방식으로?)
    @Transactional
    public void saveRooms(List<JsonNode> rows) {
        long totalStart = System.currentTimeMillis();

        // 이미 DB에 존재하는 apiId 조회
        Set<String> existingIds = new HashSet<>(roomRepository.findAllApiIds());

        List<RoomEntity> entities = rows.parallelStream()
                .map(row -> convertRowToEntity(row, existingIds))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<RoomEntity> batch = new ArrayList<>();
        int batchSize = 1000;

        for (RoomEntity entity : entities) {
            batch.add(entity);
            existingIds.add(entity.getApiId()); // 중복 방지
            if (batch.size() == batchSize) {
                saveBatch(batch);
            }
        }

        if (!batch.isEmpty()) {
            saveBatch(batch);
        }

        long totalEnd = System.currentTimeMillis();
    }

    // 배치 저장 처리
    private void saveBatch(List<RoomEntity> batch) {
        long dbStart = System.currentTimeMillis();
        roomRepository.saveAll(batch);
        roomRepository.flush();
        entityManager.clear();
        long dbEnd = System.currentTimeMillis();
        batch.clear();
    }

    // Row -> Entity 변환
    private RoomEntity convertRowToEntity(JsonNode row, Set<String> existingIds) {
        String ctrtDay = row.path("CTRT_DAY").asText("");
        String gu = row.path("CGG_NM").asText("");
        String dong = row.path("STDG_NM").asText("");
        String mno = row.path("MNO").asText("");
        String sno = row.path("SNO").asText("");
        String building = row.path("BLDG_NM").asText("");


        // 필수 필드 검증
        if (ctrtDay.isEmpty() || gu.isEmpty() || dong.isEmpty()) {
            return null;
        }

        // 주소 불완전 시 스킵
        if ((mno.isEmpty() || mno.equals("0")) && building.isEmpty()) {
            return null;
        }

        // 2024년 1월 1일 이전 계약 제외
        if (ctrtDay.compareTo("20240101") < 0) {
            return null;
        }

        // 고유 ID 생성
        String apiId = ctrtDay + "_" + gu + "_" + dong + "_" + mno + "-" + sno + "_" + building;
        if (apiId.trim().isEmpty()) {
            return null;
        }

        // 이미 존재하면 skip
        if (existingIds.contains(apiId)) {
            return null;
        }

        // 신규 매물만 Entity 생성
        String address = AddressUtil.buildAddress(row);

        RoomEntity entity = new RoomEntity();
        entity.setApiId(apiId);
        entity.setGu(gu);
        entity.setDong(dong);
        entity.setLotNumber(mno + "-" + sno);
        entity.setBuilding(building);
        entity.setAddressFull(address);
        entity.setContractDate(ctrtDay);
        entity.setRentType(row.path("RENT_SE").asText(""));

        entity.setDeposit(row.path("GRFE").asDouble(0));
        entity.setRentFee(row.path("RTFE").asDouble(0));
        entity.setArea(row.path("RENT_AREA").asDouble(0));
        entity.setFloor(row.path("FLR").asInt(0));
        entity.setArchYear(row.path("ARCH_YR").asInt(0));
        entity.setReceiptYear(row.path("RCPT_YR").asInt(0));

        entity.setX(0.0);
        entity.setY(0.0);

        entity.setGeocodeStatus("PENDING");
        entity.setGeocodeAttempts(0);

        return entity;
    }

    public void saveRoomWithCoordinate(JsonNode row) {
        RoomEntity entity = convertRowToEntity(row, new HashSet<>());
        if (entity == null) return;

        if (roomRepository.existsByApiId(entity.getApiId())) {
            System.out.println("[중복] 이미 존재하는 매물: " + entity.getApiId());
            return;
        }

        try {
            String cleanAddress = AddressUtil.clean(entity.getAddressFull());
            double[] coords = kakaoApiUtil.getCoordinatesByAddress(cleanAddress);


            if (coords != null) {
                entity.setX(coords[0]);
                entity.setY(coords[1]);
                entity.setGeocodeStatus("SUCCESS");
            } else {
                entity.setGeocodeStatus("FAILED");
            }

            entity.setGeocodeAttempts(entity.getGeocodeAttempts() + 1);

            // DB 저장
            roomRepository.save(entity);

            // API 과호출 방지
            Thread.sleep(100);

        } catch (Exception e) {
            entity.setGeocodeStatus("ERROR");
            e.printStackTrace();
        }
    }

    // 전체 삭제
    public void deleteAllRooms() {
        roomRepository.deleteAll();
    }
}
