package com.suwontrip.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// git config --list	                   적용 중인 Git 설정 전체 확인
// git config --global --list	           사용자 전체 설정 확인
// git config --local --list	           현재 저장소 설정 확인
// git config --global user.name "이름"	    전역 커밋 작성자 이름 설정
// git config --global user.email "이메일"  전역 커밋 작성자 이메일 설정
// git config --global init.defaultBranch main	새 저장소의 기본 브랜치명 설정

// git status	            변경된 파일 확인
// git status -sb	        현재 브랜치와 변경 상태 간략 확인
// git branch	            로컬 브랜치 목록
// git branch -a	        로컬·원격 브랜치 목록
// git remote -v	        연결된 원격 저장소 확인
// git diff	                아직 staging하지 않은 수정 내용
// git diff --cached    	커밋할 staging 내용
// git switch main	        main으로 이동

// git switch -c feature/backend/14-initial-setup	브랜치 생성 + 이동
// git switch feature/backend/14-initial-setup	    기존 작업 브랜치로 이동
// git fetch origin	                                개인 Fork의 최신 원격 정보 가져오기
// git fetch upstream	                            SeoJing 원본 저장소의 최신 정보 가져오기
// git merge --ff-only upstream/main            	가능한 경우 현재 브랜치를 원본 main까지 fast-forward
// git branch -d 브랜치명	                         병합이 끝난 로컬 브랜치 삭제

// docker version	      Docker Client·Engine 버전 확인
// docker info	          Docker Engine 정보 확인
// docker ps	          실행 중인 컨테이너
// docker ps -a	          종료된 컨테이너 포함 전체 목록
// docker images	      다운로드·빌드한 이미지 목록
// docker logs 컨테이너명	컨테이너 로그
// docker exec -it 컨테이너명 sh	실행 중인 컨테이너에 셸로 접속
// docker stop 컨테이너명	        컨테이너 정지
// docker start 컨테이너명	        컨테이너 재시작
// docker volume ls	              볼륨 목록

// docker compose config	      Compose 설정 해석·검증
// docker compose up -d	          백그라운드에서 서비스 실행
// docker compose up --build -d	  이미지 빌드 후 서비스 실행
// docker compose ps	          프로젝트 서비스 상태
// docker compose logs -f	      실시간 로그
// docker compose logs backend    백엔드 로그
// docker compose build backend	  백엔드 이미지만 빌드
// docker compose restart backend	백엔드 컨테이너 재시작
// docker compose exec backend sh	백엔드 컨테이너 내부 접속
// docker compose down	            서비스·기본 네트워크 종료, 볼륨 유지

@SpringBootApplication
public class SuwonTripApplication {

    public static void main(String[] args) {
        SpringApplication.run(SuwonTripApplication.class, args);
    }
}
