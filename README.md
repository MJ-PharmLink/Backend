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
│   ├── config/ 스프링 설정 (Security, 초기 관리자 계정 생성 등)
│   ├── security/ JWT 필터, JWT 유틸
│   ├── exception/ 전역 예외 처리 (ErrorCode, BusinessException, GlobalExceptionHandler)
│   ├── response/ 공통 응답 구조 (ApiResponse, ErrorResponse, Meta, Pagination)
│   └── common/ 공통 베이스 클래스 (BaseEntity, PageRequestFactory)
│
└── domain/ 기능별 도메인
    ├── auth/ 로그인, 토큰 재발급 (담당: 이예린)
    ├── user/ 사용자 계정 관리 (담당: 이예린)
    ├── partner/ 거래처 관리 (담당: 이예린)
    ├── company/ 회사 정보 (담당: 이예린)
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
  secret: "팀에서_정한_JWT_시크릿_키"   # 32바이트(영문 32자) 이상, 짧으면 실행 시 오류

admin:
  username: 팀에서_정한_관리자_아이디
  password: "팀에서_정한_관리자_비밀번호"   # 8~64자
```

관리자 계정 정보는 git에 올리지 않고 팀 채팅방에서 따로 공유합니다.

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

> 이전에 `ddl-auto: update`로 테이블이 만들어진 로컬 DB가 있다면, Flyway가 정상 적용되도록 볼륨을 지우고 다시 띄워주세요.
>
> ```bash
> docker-compose down -v
> docker-compose up -d
> ```

### 4. 프로젝트 실행

```bash
./gradlew bootRun
```

콘솔에 `Started BackendApplication`이 뜨고 에러 없이 종료되지 않으면 성공입니다.

처음 실행하면 Flyway가 테이블 생성(V1)과 초기 데이터 입력(V2)을 자동으로 수행합니다. 이어서 DB에 ADMIN 계정이 하나도 없으면 `application-secret.yml`의 `admin.*` 값으로 관리자 계정을 1회 생성합니다(`AdminAccountInitializer`).

## DB 마이그레이션 (Flyway)

DB 스키마는 JPA 자동 생성(`ddl-auto`)이 아니라 Flyway 마이그레이션으로 관리합니다. `ddl-auto`는 `validate`라서 엔티티와 테이블이 어긋나면 실행 시 에러가 납니다.

- 위치: `src/main/resources/db/migration/`
- `V1__init_schema.sql`: DB 설계서 16개 테이블 / `V2__seed_data.sql`: 초기 데이터(회사, 본사 창고, 카테고리 5종). 관리자 계정은 보안상 마이그레이션에 넣지 않습니다.
- 이미 dev에 올라간 마이그레이션 파일은 **절대 수정하지 마세요.** 컬럼 추가·변경이 필요하면 다음 번호로 새 파일을 만듭니다. (예: `V3__add_xxx_column.sql`)
- 버전 번호가 겹치지 않도록 새 마이그레이션을 만들기 전에 dev를 pull 받아 마지막 번호를 확인해주세요.
- DB 시간 저장 기준은 KST(Asia/Seoul)입니다.

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

**목록 응답 (페이지네이션)**
```json
{
  "success": true,
  "data": [ ],
  "pagination": { "page": 1, "page_size": 20, "total_count": 120, "total_pages": 6 },
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

응답 필드는 전역 설정으로 snake_case로 변환됩니다. DTO 필드는 Java 관례대로 camelCase(`unitPrice`)로 작성하면 JSON에서는 `unit_price`가 됩니다. 쿼리 파라미터는 `@RequestParam(name = "page_size")`처럼 이름을 직접 지정해주세요.

**사용 예시**
```java
// 단건 응답
return ApiResponse.success(response);

// 목록 응답: page 기본 1, page_size 기본 20, 최대 100
Pageable pageable = PageRequestFactory.of(page, pageSize);
Page<PartnerResponse> result = partnerService.getPartners(pageable);
return ApiResponse.success(result);

// 업무 규칙 위반: 명세서 16장 Error Code를 ErrorCode enum으로 사용
throw new BusinessException(ErrorCode.PARTNER_NOT_FOUND);
```

`@Valid` 검증 실패(400), enum 값 오류(422), 처리되지 않은 예외(500) 등은 `GlobalExceptionHandler`가 공통 오류 응답으로 변환합니다.

## 인증 / 권한 (Security)

- 모든 API는 `Authorization: Bearer {access_token}` 헤더가 필요합니다. 예외는 `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` 두 개뿐입니다.
- 토큰이 없거나 만료·위조되면 `401 UNAUTHORIZED`, 역할 권한이 없으면 `403 FORBIDDEN`이 공통 오류 형식으로 응답됩니다. 컨트롤러에서 따로 처리할 필요가 없습니다.
- 역할은 `ADMIN`(관리자), `SALES`(영업담당), `WAREHOUSE`(창고담당) 3가지입니다.

**로그인 사용자 꺼내기**

access token에 담긴 `user_id`, `role`이 `UserPrincipal`로 들어옵니다. DB 조회 없이 바로 쓸 수 있습니다.

```java
@PostMapping
public ApiResponse<OrderResponse> createOrder(@AuthenticationPrincipal UserPrincipal principal,
                                              @Valid @RequestBody OrderCreateRequest request) {
    Long userId = principal.userId();   // created_by 등에 사용
    Role role = principal.role();
    return ApiResponse.success(orderService.create(userId, request));
}
```

**역할별 권한 지정**

명세서 2장 "권한" 칸을 보고 컨트롤러 메서드(또는 클래스)에 `@PreAuthorize(AccessRole.XXX)`를 붙입니다. 문자열을 직접 쓰지 말고 `AccessRole` 상수를 사용해주세요.

| 명세서 권한 | 사용할 상수 |
|---|---|
| 관리자 | `AccessRole.ADMIN` |
| 관리자, 영업 | `AccessRole.ADMIN_SALES` |
| 관리자, 창고 | `AccessRole.ADMIN_WAREHOUSE` |
| 관리자, 영업, 창고 / 로그인 사용자 | `AccessRole.ALL` |

```java
@PreAuthorize(AccessRole.ADMIN_SALES)   // 거래처 등록: 관리자, 영업
@PostMapping
public ApiResponse<PartnerResponse> createPartner(@Valid @RequestBody PartnerCreateRequest request) { ... }
```

권한이 없으면 `403 FORBIDDEN`이 자동으로 응답됩니다.

**인증 API (명세서 3장)**

| API | 설명 |
|---|---|
| `POST /api/v1/auth/login` | 로그인. access token(1시간)과 refresh token(14일) 발급 |
| `POST /api/v1/auth/refresh` | refresh token으로 access token 재발급 |
| `POST /api/v1/auth/logout` | refresh token 폐기 (204) |
| `GET /api/v1/auth/me` | 내 정보 조회 |

**사용자 관리 API (관리자 전용)**

| API | 설명 |
|---|---|
| `GET /api/v1/users` | 사용자 목록. `role` 필터, `include_inactive`(기본 false), 페이지네이션 |
| `POST /api/v1/users` | 계정 생성 (201). 비밀번호 8~64자, 중복 아이디 409, role 값 오류 422 |
| `PATCH /api/v1/users/{user_id}` | name, role, is_active, password 중 보낸 값만 수정 |
| `DELETE /api/v1/users/{user_id}` | 비활성화 (204) |

비밀번호·역할 변경, 비활성화 시 해당 사용자의 refresh token이 같은 트랜잭션에서 폐기됩니다(`AuthService.revokeRefreshToken`). 이미 발급된 access token은 만료(1시간)까지 유효합니다.

관리자는 본인 계정의 역할 변경과 비활성화(PATCH `role`, `is_active: false`, DELETE)를 할 수 없습니다(422). 활성 관리자가 0명이 되는 것을 막기 위한 규칙이며, 본인 이름·비밀번호 수정은 가능합니다.

**거래처 관리 API**

| API | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/business-partners` | 관리자, 영업, 창고 | 거래처 목록. `partner_type`, `keyword`(거래처명·사업자등록번호), `include_inactive`(기본 false), 페이지네이션 |
| `POST /api/v1/business-partners` | 관리자, 영업 | 거래처 등록 (201). 사업자등록번호 `000-00-00000` 형식, 중복 409 |
| `GET /api/v1/business-partners/{partner_id}` | 관리자, 영업, 창고 | 거래처 상세 (비활성 거래처도 조회) |
| `PUT /api/v1/business-partners/{partner_id}` | 관리자, 영업 | 거래처 정보 전체 교체. `partner_type`은 변경 불가 |
| `DELETE /api/v1/business-partners/{partner_id}` | 관리자, 영업 | 거래처 비활성화 (204). 승인 대기 주문(`PENDING`)이나 납품 완료 전 납품(`WAITING`, `SHIPPED`)이 있으면 409 `PARTNER_IN_USE`, 이미 비활성이면 변경 없이 204 |
| `GET /api/v1/business-partners/{partner_id}/transactions` | 관리자, 영업 | 거래 이력(주문·매출·매입) 최신순. `type`, `start_date`·`end_date`(KST 날짜, 시작일 > 종료일이면 422 `INVALID_DATE_RANGE`), 페이지네이션 |

다른 도메인(상품·주문·매입)에서 거래처를 연결할 때는 `PartnerService`의 공용 메서드를 사용하세요.

```java
BusinessPartner supplier = partnerService.getActiveSupplier(request.supplierId()); // 상품 등록, 매입 등록
BusinessPartner customer = partnerService.getActiveCustomer(request.partnerId());  // 주문 등록
```

없는 거래처는 404 `PARTNER_NOT_FOUND`, 유형이 다르면 422 `INVALID_PARTNER_TYPE`, 비활성이면 409 `PARTNER_INACTIVE`를 자동으로 던집니다.

거래처 비활성화와 거래 이력은 `orders`, `deliveries`, `sales`, `purchases` 테이블을 native query로 직접 읽습니다(`NativePartnerTransactionReader`, DB 설계서 6장 `v_partner_transactions`). 주문·납품·매출·매입 엔티티가 없어도 동작하며, 해당 테이블의 컬럼명이나 상태 값(`PENDING`, `WAITING`, `SHIPPED`)이 바뀌면 이 클래스도 함께 수정해야 합니다.

Postman 컬렉션은 `postman/` 폴더에 기능별로 있습니다(`PharmLink-auth`, `PharmLink-user`, `PharmLink-partner`). 관리자 아이디·비밀번호는 컬렉션이 아니라 Postman Environment(`admin_username`, `admin_password`)에 넣어주세요. `postman/environments/`는 `.gitignore` 처리되어 있습니다.

**시간 값**

`BaseEntity`의 `created_at`, `updated_at`과 엔티티의 일시 필드는 `Instant`를 사용합니다. DB에는 KST로 저장되고, 응답은 명세서 1.5대로 UTC(`2026-09-10T05:00:00Z`)로 나갑니다. 날짜만 필요한 필드(유통기한, 매입일 등)는 `LocalDate`를 사용합니다.

## 브랜치 전략

- `main`: 배포용 (직접 커밋 금지)
- `dev`: 개발 통합 브랜치
- `feat/{기능명}`: 기능 개발 브랜치 (예: `feat/auth`, `feat/partner`, `feat/order`)

기능 개발 완료 후 `dev` 브랜치로 Pull Request를 올려서 팀원 리뷰 후 머지합니다.

## 주의사항

- `application-secret.yml`, `.env`에 들어가는 비밀번호/키 값은 절대 커밋하지 마세요.
- 도메인 패키지 하위 폴더 이름(`controller`, `service`, `repository`, `entity`, `dto`)은 임의로 바꾸지 말고 통일해주세요.
- `docker-compose.yml`, `container_name` 등 공용 설정 파일을 로컬에서 임의로 수정하고 커밋하지 마세요. 변경이 필요하면 팀 채팅방에 먼저 공유해주세요.