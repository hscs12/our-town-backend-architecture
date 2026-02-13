package com.example.seoulapi.util;

import com.fasterxml.jackson.databind.JsonNode;

// 주소 파싱/정제
public class AddressUtil {
    // 서울특별시 + 구 + 동 + 지번 형태로 주소 생성
    public static String buildAddress(JsonNode row) {
        String gu = row.path("CGG_NM").asText("");
        String dong = row.path("STDG_NM").asText("");   // 법정동
        String mno = row.path("MNO").asText("");    // 본번
        String sno = row.path("SNO").asText("");    // 부번(0이면 제외)
        String building = row.path("BLDG_NM").asText("");   // 필요할지 모르겠

        // 앞자리 0 제거
        if (!mno.isEmpty()) {
            mno = String.valueOf(Integer.parseInt(mno));
        }
        if (!sno.isEmpty()) {
            sno = String.valueOf(Integer.parseInt(sno));
        }

        // 본번-부번 조합
        String lotNumber = sno.isEmpty() ? mno : mno + "-" + sno;

        // building이 lotNumber랑 같거나 괄호 포함 지번이면 무시
        if (building.equals(lotNumber) || building.contains("(" + lotNumber + ")")) {
            building = "";
        }


        // 최종 주소 문자열
        return String.format("서울특별시 %s %s %s %s",
                gu,
                dong,
                lotNumber,
                building
        ).trim();

    }

    public static String clean(String address) {
        if (address == null || address.isBlank()) return "";

        String cleaned = address;

        // 괄호 제거
        cleaned = cleaned.replaceAll("\\([^)]*\\)", "");

        // 중복된 "구 구" 제거
        cleaned = cleaned.replaceAll("(서울특별시\\s*[가-힣]+구)\\s+\\1", "$1");

        // sno = 0 제거 ("128-0" → "128")
        cleaned = cleaned.replaceAll("(\\d+)-0\\b", "$1");

        // 도로명주소 뒤에 붙은 텍스트 제거
        cleaned = cleaned.replaceAll("(서울특별시\\s*[가-힣]+구\\s*[가-힣0-9]+(동|가)?)\\s+[0-9]+.*", "$1");

        // "서울특별시 구 동 지번" 패턴 정규화
        cleaned = cleaned.replaceAll(
                "(서울특별시\\s*[가-힣]+구\\s*[가-힣0-9]+(동|가)?)\\s*(\\d+(?:-\\d+)?)?.*",
                "$1 $3"
        );

        // 공백 정리
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned;
    }
}