# AI 모듈(공개 레포 제외)

AI 팀에서 구현한 점수 산출(score_model.py) 및 백분위 계산(compute_percentile.py) 스크립트와 관련 데이터는
협업 규칙에 따라 공개 GitHub 레포지토리에서 제외했습니다.

백엔드는 로컬 환경에서 ProcessBuilder로 해당 스크립트를 실행하고, stdout으로 출력되는 JSON 결과를 수신하여 히트맵/랭킹 데이터를 구성합니다.
