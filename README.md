# PharmLink Backend

## 기술 스택

- Java 21
- Spring Boot (Gradle)
- Spring Data JPA
- Spring Security + JWT
- MySQL 8.0
- Docker (로컬 개발용 DB)


## 프로젝트 구조

기능(도메인) 단위로 패키지를 나누는 Package by Feature 구조를 따릅니다.

```
com.pharmlink.backend
├── BackendApplication.java
│
├── global/ 공통 코드
│   ├── config/ 스프링 설정 (Security 등)
│   ├── security/ JWT 필터, JWT 유틸
│   ├── exception/ 전역 예외 처리
│   ├── response/ 공통 응답 구조 (ApiResponse, ErrorResponse, Meta)
│   └── common/ 공통 베이스 클래스 (BaseEntity 등)
│
└── domain/ 기능별 도메인
    ├── auth/ 로그인, 토큰 재발급 (담당: 이예린)
    ├── user/ 사용자 계정 관리 (담당: 이예린)
    ├── partner/ 거래처 관리 (담당: 이예린)
    ├── item/ 상품 마스터 (담당: 최영선)
    ├── inventory/ 재고 관리 (담당: 최영선)
    ├── order/ 주문 (담당: 김소리)
    ├── delivery/ 납품 (담당: 김소리)
    ├── purchase/ 매입 (담당: 김소리)
    ├── sales/ 매출/마진 (담당: 김소리)
    └── dashboard/ 대시보드 (담당: 김소리)
```

각 도메인 패키지 하위 폴더는 아래 5개 이름으로 통일합니다. (도메인 성격에 따라 일부 폴더는 없을 수 있습니다)

`controller/` `service/` `repository/` `entity/` `dto/`



## 최초 세팅 방법

### 1. dev 브랜치 pull

```bash
git checkout dev
git pull origin dev
```

### 2. 환경설정 파일 만들기

아래 2개 파일은 민감 정보(비밀번호, 시크릿 키)가 들어가기 때문에 `.gitignore` 처리되어 있습니다. 직접 만들어야 합니다.

**① `src/main/resources/application-secret.yml`**

```yaml
spring:
  datasource:
    username: root
    password: 본인_MySQL_비밀번호

jwt:
  secret: "팀에서_정한_JWT_시크릿_키"
```

**② 프로젝트 루트에 `.env`**

`.env.example` 파일을 복사해서 `.env`로 이름을 바꾸고, 값을 채워 넣으세요.

```bash
cp .env.example .env
```

### 3. DB 실행 (Docker)

```bash
docker-compose up -d
```

정상 실행 확인:

```bash
docker ps
```

`pharm-erp-db` 컨테이너가 떠 있으면 성공입니다.

### 4. 프로젝트 실행

```bash
./gradlew bootRun
```

콘솔에 `Started BackendApplication`이 뜨고 에러 없이 종료되지 않으면 성공입니다.

## 공통 응답 구조

모든 API 응답은 아래 형식을 따릅니다. `global/response/`에 정의된 `ApiResponse`, `ErrorResponse`를 사용하세요.

**성공 응답**
```json
{
  "success": true,
  "data": { },
  "meta": { "request_id": "uuid-v4", "timestamp": "2026-09-10T12:00:00Z" }
}
```

**오류 응답**
```json
{
  "success": false,
  "error": { "code": "VALIDATION_ERROR", "message": "...", "details": [] },
  "meta": { "request_id": "uuid-v4", "timestamp": "2026-09-10T12:00:00Z" }
}
```

## 브랜치 전략

- `main`: 배포용 (직접 커밋 금지)
- `dev`: 개발 통합 브랜치
- `feature/{담당자}-{기능명}`: 각자 기능 개발 브랜치 (예: `feature/nirey-auth`)

기능 개발 완료 후 `dev` 브랜치로 Pull Request를 올려서 팀원 리뷰 후 머지합니다.

## 주의사항

- `application-secret.yml`, `.env`에 들어가는 비밀번호/키 값은 절대 커밋하지 마세요.
- 도메인 패키지 하위 폴더 이름(`controller`, `service`, `repository`, `entity`, `dto`)은 임의로 바꾸지 말고 통일해주세요.
- `docker-compose.yml`, `container_name` 등 공용 설정 파일을 로컬에서 임의로 수정하고 커밋하지 마세요. 변경이 필요하면 팀 채팅방에 먼저 공유해주세요.