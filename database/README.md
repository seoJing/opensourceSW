# 데이터베이스

로컬 개발에서는 Docker Compose의 `db` 서비스가 PostgreSQL을 실행합니다. 데이터는 `suwon_trip_postgres_data` named volume에 저장되며 일반적인 `docker compose down`으로는 삭제되지 않습니다.

현재 단계에는 스키마, 마이그레이션, POI 데이터가 없습니다. 데이터 구조가 팀에서 합의되기 전에는 임의로 추가하지 않습니다.
