# AI 작업 규칙

이 파일은 이 저장소에서 AI 도구가 따라야 하는 작업 규칙의 정본입니다.

## 프로젝트 목적과 현재 단계

이 프로젝트는 5인 팀이 수원시의 랜덤 여행 코스 추천 웹 서비스를 학습 목적으로 개발하기 위한 모노레포입니다. 현재 단계는 팀원을 초대하고 동일한 Docker 기반 환경에서 개발을 시작하기 위한 **초기 저장소 구성 단계**입니다.

POI, 추천 로직, 성향 테스트, 지도, 외부 API, 로그인, 관리자, 실제 여행 화면, AI 연동 등 비즈니스 기능의 사양은 아직 정해지지 않았습니다. 명시적인 Issue와 요청 없이 임의로 설계하거나 구현하지 마세요.

## 디렉터리별 책임

- `frontend/`: React/Vite 상태 확인 화면과 프론트엔드 테스트
- `backend/`: Spring Boot 애플리케이션과 Actuator health 설정
- `database/`: 향후 데이터베이스 초기화 자료의 위치 설명
- `scripts/`: Windows와 macOS/Linux용 실행·중지·테스트 스크립트
- `docs/`: 현재 아키텍처 문서
- `.github/`: Pull Request 템플릿과 CI 워크플로

## 표준 명령

Windows PowerShell:

```powershell
./scripts/start.ps1
./scripts/test.ps1
./scripts/stop.ps1
```

macOS/Linux:

```sh
sh scripts/start.sh
sh scripts/test.sh
sh scripts/stop.sh
```

Docker Compose는 `frontend`, `backend`, `db` 세 서비스만 기본 실행하며, `frontend → backend → db` 순서로 연결됩니다.

## 변경 원칙

1. 변경 전에 기존 파일, 현재 Git 상태, 이 파일과 관련 README를 확인합니다.
2. 요청 범위 밖의 기능 추가나 리팩터링을 하지 않습니다.
3. 관련 없는 파일의 포맷이나 줄바꿈을 변경하지 않습니다.
4. 새 라이브러리가 필요하면 PR에 필요성, 대안, 선택 이유를 기록합니다.
5. API나 프론트·백엔드 계약을 변경하면 문서와 테스트를 함께 변경합니다.
6. 프론트·백엔드 간 계약을 추측하지 않습니다. 합의되지 않은 endpoint나 데이터 구조를 만들지 않습니다.
7. POI와 추천 사양은 미정이므로 샘플 구현도 임의로 추가하지 않습니다.
8. 테스트를 삭제·비활성화하거나 실패를 무시해서 통과시키지 않습니다.
9. 오류를 숨기는 빈 예외 처리나 강제 성공 처리를 추가하지 않습니다.
10. `.env`, 키, 토큰, 비밀번호와 실제 비밀정보를 커밋하지 않습니다.
11. `node_modules`, `target`, `dist`, coverage, 로그, 캐시와 기타 생성 파일을 커밋하지 않습니다. 단, 재현 가능한 설치를 위한 `package-lock.json`과 Maven Wrapper 파일은 커밋합니다.
12. 작은 PR 단위로 변경하고 PR 하나에 한 가지 목적만 둡니다.

## Git과 PR

- `main`, `develop`에 직접 push하지 않습니다.
- 작업 전에 Issue를 만들고 Issue 하나당 브랜치 하나를 사용합니다.
- 작업 브랜치에서 `develop`으로 PR을 만들고 최소 1명의 리뷰를 받습니다.
- 가능하면 Squash merge합니다.
- 자세한 브랜치 이름과 절차는 `CONTRIBUTING.md`를 따릅니다.

## 완료 전 검증과 보고

관련 영역의 lint, 테스트, 빌드를 실행합니다. 통합 환경에 영향을 주면 `docker compose config`, 이미지 빌드, 세 서비스 health, 프론트엔드와 `/api/health` 응답까지 확인합니다.

작업 결과에는 다음을 보고합니다.

- 변경한 파일과 핵심 내용
- 실행한 검증 명령과 결과
- 확인하지 못한 항목과 이유
- 남은 문제 또는 후속 합의가 필요한 사항
