# PharmLink Backend

의약품 유통 ERP(PharmLink)의 백엔드 API 서버입니다.

## 기술 스택

- Java 21
- Spring Boot 4.1.1 (Gradle)
- Spring Data JPA
- Spring Security + JWT (jjwt 0.12.6)
- Flyway (DB 마이그레이션)
- MySQL 8.0
- OpenPDF 2.0.3 (납품서 PDF. 한글은 `src/main/resources/fonts/`의 나눔고딕 폰트 사용)
- Docker (로컬 개발용 DB)


## 프로젝트 구조

기능(도메인) 단위로 패키지를 나누는 Package by Feature 구조를 따릅니다.

```
com.pharmlink.backend
├── BackendApplication.java
│
├── global/ 공통 코드
│   ├── config/ 초기 관리자 계정 생성 (AdminAccountInitializer)
│   ├── security/ Security 설정(SecurityConfig, CORS), JWT 필터·유틸, 401/403 응답, AccessRole
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

리소스는 `src/main/resources/` 아래 `application.yaml`, `db/migration/`(Flyway), `fonts/`(납품서 PDF용 나눔고딕과 라이선스 `OFL.txt`)가 있습니다.



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
  # name: 관리자   # 선택. 생략하면 "관리자"
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

> `./gradlew test`의 `@SpringBootTest` 테스트(매입 → 주문 → 납품 흐름 통합 테스트 포함)는 로컬 MySQL에 접속합니다. DB를 띄우고 `application-secret.yml`을 만든 뒤 실행해주세요.

## DB 마이그레이션 (Flyway)

DB 스키마는 JPA 자동 생성(`ddl-auto`)이 아니라 Flyway 마이그레이션으로 관리합니다. `ddl-auto`는 `validate`라서 엔티티와 테이블이 어긋나면 실행 시 에러가 납니다.

- 위치: `src/main/resources/db/migration/`
- 현재 dev에는 2개 파일이 있습니다.
  - `V1__init_schema.sql`: DB 설계서 16개 테이블
  - `V2__seed_data.sql`: 초기 데이터(회사 정보 1건, 본사 창고, 카테고리 5종). 관리자 계정은 보안상 마이그레이션에 넣지 않습니다.
- 이미 dev에 올라간 마이그레이션 파일은 **절대 수정하지 마세요.** 컬럼 추가·변경이 필요하면 다음 번호로 새 파일을 만듭니다. (예: `V3__add_xxx_column.sql`)
- 버전 번호가 겹치지 않도록 새 마이그레이션을 만들기 전에 dev를 pull 받아 마지막 번호를 확인해주세요.
- DB 시간 저장 기준은 KST(Asia/Seoul)입니다.

## 공통 응답 구조

모든 API 응답은 아래 형식을 따릅니다. `global/response/`에 정의된 `ApiResponse`, `ErrorResponse`를 사용하세요. (예외: 납품서 PDF `GET /api/v1/deliveries/{delivery_id}/document`는 PDF 파일을 그대로 반환하고, 오류만 아래 오류 형식으로 응답합니다.)

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

// 목록 응답: Page를 넘기면 data와 pagination이 함께 나갑니다
// 페이지 규칙은 PageRequestFactory.of(page, pageSize): page 기본 1, page_size 기본 20, 최대 100
Page<OrderResponse> result = orderService.getOrders(status, partnerId, startDate, endDate, page, pageSize);
return ApiResponse.success(result);

// 업무 규칙 위반: 명세서 16장 Error Code를 ErrorCode enum으로 사용
throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
```

`@Valid` 검증 실패·필수 쿼리 파라미터 누락(400), enum 값 오류(422), 처리되지 않은 예외(500) 등은 `GlobalExceptionHandler`가 공통 오류 응답으로 변환합니다.

**시간 값**

`BaseEntity`의 `created_at`, `updated_at`과 엔티티의 일시 필드는 `Instant`를 사용합니다. DB에는 KST로 저장되고, 응답은 명세서 1.5대로 UTC(`2026-09-10T05:00:00Z`)로 나갑니다. 날짜만 필요한 필드(유통기한, 매입일, 매출일 등)는 `LocalDate`를 사용합니다.

## 인증 / 권한 (Security)

- 모든 API는 `Authorization: Bearer {access_token}` 헤더가 필요합니다. 예외는 `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` 두 개뿐입니다.
- 토큰이 없거나 만료·위조되면 `401 UNAUTHORIZED`, 역할 권한이 없으면 `403 FORBIDDEN`이 공통 오류 형식으로 응답됩니다. 컨트롤러에서 따로 처리할 필요가 없습니다.
- 역할은 `ADMIN`(관리자), `SALES`(영업담당), `WAREHOUSE`(창고담당) 3가지입니다.
- CORS는 로컬 프론트 개발 서버 `http://localhost:5173`만 허용합니다(`SecurityConfig`의 `ALLOWED_ORIGINS`). 배포 주소가 정해지면 여기에 추가합니다.

**로그인 사용자 꺼내기**

access token에 담긴 `user_id`, `role`이 `UserPrincipal`로 들어옵니다. DB 조회 없이 바로 쓸 수 있습니다.

```java
@PreAuthorize(AccessRole.ADMIN_SALES)
@PostMapping
public ResponseEntity<ApiResponse<OrderCreateResponse>> createOrder(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody OrderCreateRequest request) {
    Long userId = principal.userId();   // created_by 등에 사용 (역할은 principal.role())
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(orderService.createOrder(userId, request)));
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
public ResponseEntity<ApiResponse<PartnerDetailResponse>> createPartner(@Valid @RequestBody PartnerCreateRequest request) { ... }
```

권한이 없으면 `403 FORBIDDEN`이 자동으로 응답됩니다.

## API

dev에 구현된 API를 명세서 장 순서대로 정리했습니다. Base URL은 `/api/v1`이고, 권한 칸은 각 컨트롤러의 `@PreAuthorize(AccessRole.X)` 기준입니다.

**공통 규칙**

- 목록 API는 `page`(기본 1), `page_size`(기본 20, 최대 100)로 페이지네이션합니다. 재고 부족 품목(12.2)만 `page_size` 기본값이 10입니다.
- `start_date`, `end_date`는 KST 날짜(`YYYY-MM-DD`)이고, 시작일이 종료일보다 늦으면 422 `INVALID_DATE_RANGE`입니다.
- 등록 API는 201, 삭제(비활성화)와 로그아웃은 204로 응답합니다. 없는 ID는 도메인별 `*_NOT_FOUND`(404)입니다.

### 인증 (3장)

| API | 권한 | 설명 |
|---|---|---|
| `POST /api/v1/auth/login` | 없음 (공개) | 로그인. access token(1시간)과 refresh token(14일) 발급. 불일치 401 `INVALID_CREDENTIALS`, 비활성 계정 403 `ACCOUNT_DISABLED` |
| `POST /api/v1/auth/refresh` | 없음 (공개) | refresh token으로 access token 재발급 (refresh token은 그대로 유지) |
| `POST /api/v1/auth/logout` | 관리자, 영업, 창고 | refresh token 폐기 (204) |
| `GET /api/v1/auth/me` | 관리자, 영업, 창고 | 내 정보 조회 |

### 사용자 (4장)

| API | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/users` | 관리자 | 사용자 목록. `role`, `include_inactive`(기본 false) |
| `POST /api/v1/users` | 관리자 | 계정 생성 (201). 비밀번호 8~64자, 중복 아이디 409 `DUPLICATE_USERNAME` |
| `PATCH /api/v1/users/{user_id}` | 관리자 | `name`, `role`, `is_active`, `password` 중 보낸 값만 수정. 비밀번호·역할 변경, 비활성화 시 refresh token 폐기. 본인 계정의 역할 변경·비활성화는 422 |
| `DELETE /api/v1/users/{user_id}` | 관리자 | 비활성화 (204). refresh token 폐기, 본인 계정은 422 |

### 거래처 (5장)

| API | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/business-partners` | 관리자, 영업, 창고 | 거래처 목록. `partner_type`, `keyword`(거래처명·사업자등록번호), `include_inactive`(기본 false) |
| `POST /api/v1/business-partners` | 관리자, 영업 | 거래처 등록 (201). 사업자등록번호 `000-00-00000` 형식, 중복 409 `DUPLICATE_BUSINESS_NUMBER` |
| `GET /api/v1/business-partners/{partner_id}` | 관리자, 영업, 창고 | 거래처 상세 (비활성 거래처도 조회) |
| `PUT /api/v1/business-partners/{partner_id}` | 관리자, 영업 | 거래처 정보 전체 교체. `partner_type`은 변경 불가 |
| `DELETE /api/v1/business-partners/{partner_id}` | 관리자, 영업 | 비활성화 (204, 이미 비활성이어도 204). 승인 대기 주문이나 납품 완료 전 납품이 있으면 409 `PARTNER_IN_USE` |
| `GET /api/v1/business-partners/{partner_id}/transactions` | 관리자, 영업 | 거래 이력(주문·매출·매입) 최신순. `type`, `start_date`, `end_date` |

### 상품·재고 (6·7장)

dev에는 아직 상품·카테고리·창고·재고 API(컨트롤러)가 없습니다. `item/`, `inventory/` 패키지에는 엔티티·리포지토리와, 매입 등록·주문 승인에서 쓰는 재고 입출고 서비스(`InventoryStockService`)만 있습니다.

### 주문 (8장)

| API | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/orders` | 관리자, 영업 | 주문 목록 최신순. `status`, `partner_id`, `start_date`, `end_date`(주문 등록일 기준) |
| `POST /api/v1/orders` | 관리자, 영업 | 주문 등록 (201, `PENDING`). 판매단가·원가는 상품 마스터에서 스냅샷, 주문번호 `ORD-YYYYMMDD-NNNN` 채번. 수량 1 미만·같은 상품 중복 422 `INVALID_QUANTITY`, 단종 상품 409 `ITEM_INACTIVE` |
| `GET /api/v1/orders/{order_id}` | 관리자, 영업 | 주문 상세 (단가·원가 스냅샷, 연결된 `delivery_id`) |
| `POST /api/v1/orders/{order_id}/approve` | 관리자, 영업 | 주문 승인. 기본 창고에서 만료 로트를 빼고 FEFO로 재고 차감·출고 이력 기록 후 납품(`WAITING`) 생성. 재고 부족 409 `INSUFFICIENT_STOCK`(부족 상품 전체를 `details`에), 이미 승인 409 `ORDER_ALREADY_APPROVED` |
| `POST /api/v1/orders/{order_id}/cancel` | 관리자, 영업 | 주문 취소. `PENDING`만 가능(그 외 409 `INVALID_ORDER_STATUS`). 본문 `cancel_reason`은 선택(최대 200자) |

### 납품 (9장)

| API | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/deliveries` | 관리자, 영업, 창고 | 납품 목록 최신순. `status`, `partner_id`, `start_date`, `end_date`(납품 생성일 기준) |
| `GET /api/v1/deliveries/{delivery_id}` | 관리자, 영업, 창고 | 납품 상세 (품목은 주문 라인 스냅샷) |
| `POST /api/v1/deliveries/{delivery_id}/ship` | 관리자, 창고 | 출고 완료 `WAITING` → `SHIPPED`. 다른 상태면 409 `INVALID_DELIVERY_STATUS` |
| `POST /api/v1/deliveries/{delivery_id}/complete` | 관리자, 창고 | 납품 완료 `SHIPPED` → `DELIVERED`, 같은 트랜잭션에서 매출 자동 생성. 이미 매출이 있으면 409 `SALE_ALREADY_RECORDED` |
| `GET /api/v1/deliveries/{delivery_id}/document` | 관리자, 영업, 창고 | 납품서 PDF(`delivery-{id}.pdf`) 다운로드. 생성 실패 500 `PDF_GENERATION_FAILED` |

### 매입 (10장)

| API | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/purchases` | 관리자, 창고 | 매입 목록 최신 매입일순. `partner_id`, `warehouse_id`, `start_date`, `end_date`(매입일 기준) |
| `POST /api/v1/purchases` | 관리자, 창고 | 매입 등록 (201). 로트 생성 또는 합산·재고 증가·입고 이력을 한 트랜잭션으로 처리. `warehouse_id` 생략 시 기본 창고, 유통기한이 매입일보다 이르면 422 `INVALID_EXPIRY_DATE` |
| `GET /api/v1/purchases/{purchase_id}` | 관리자, 창고 | 매입 상세 (원가 스냅샷, 로트번호·유통기한) |

### 매출·마진 (11장)

| API | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/sales` | 관리자, 영업 | 매출 목록 최신 매출일순. `partner_id`, `start_date`, `end_date`(매출일 기준). 매출은 납품 완료 시에만 생성되며 등록 API는 없음 |
| `GET /api/v1/sales/{sale_id}` | 관리자, 영업 | 매출 상세와 품목별 매출·원가·마진 |
| `GET /api/v1/margins/summary` | 관리자, 영업 | 마진 집계. `start_date`·`end_date` 필수(누락 400), `group_by`(`PARTNER`/`ITEM`, 값 오류 422), `partner_id`, `item_id` |

### 대시보드 (12장)

| API | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/dashboard/summary` | 관리자, 영업, 창고 | 기간 매출·매입·마진 합계와 마진율, 현재 승인 대기 주문·출고 대기/출고 완료 납품·재고 부족·유통기한 임박/만료 건수. 기간 생략 시 이번 달 1일 ~ 오늘(KST) |
| `GET /api/v1/dashboard/low-stock-items` | 관리자, 영업, 창고 | 재고 부족 품목(활성 상품, 안전재고 > 0, 현재 수량 ≤ 안전재고) 부족 수량 큰 순. `warehouse_id` |
| `GET /api/v1/dashboard/recent-orders` | 관리자, 영업, 창고 | 최근 주문과 연결된 납품 상태. `limit` 기본 10, 1~50을 벗어나면 422 |

유통기한 임박 기준은 90일이며 `pharmlink.inventory.expiring-soon-days` 설정으로 바꿀 수 있습니다.

### 회사 정보 (13장)

| API | 권한 | 설명 |
|---|---|---|
| `GET /api/v1/company` | 관리자, 영업, 창고 | 우리 회사(`company_id = 1`) 정보 조회. 행이 없으면 404 `COMPANY_NOT_FOUND` |
| `PUT /api/v1/company` | 관리자 | 회사 정보 전체 교체. `wholesale_license_number`, `fax`, `email`은 생략하거나 빈 값이면 null |

### 다른 도메인에서 쓰는 공용 메서드

다른 도메인에서 호출하는 공용 메서드입니다. 같은 검증·계산이 필요하면 새로 만들지 말고 이 메서드를 사용해주세요.

| 메서드 | 사용하는 곳 | 설명 |
|---|---|---|
| `PartnerService.getActiveSupplier(partnerId)` | 매입 등록 | 활성 공급처 조회. 없음 404 `PARTNER_NOT_FOUND`, 유형 불일치 422 `INVALID_PARTNER_TYPE`, 비활성 409 `PARTNER_INACTIVE` |
| `PartnerService.getActiveCustomer(partnerId)` | 주문 등록 | 활성 고객사 조회. 오류는 위와 같음 |
| `CompanyService.getCompany()` | 납품서 PDF | 공급자(우리 회사) 정보 |
| `AuthService.revokeRefreshToken(userId)` | 사용자 수정·비활성화 | 해당 사용자의 refresh token 폐기 |
| `InventoryStockService.receivePurchase(...)` / `shipOrder(...)` | 매입 등록 / 주문 승인 | 재고 행을 잠근 뒤 입고(로트 생성·합산) 또는 FEFO 출고와 재고 이력 기록. 호출하는 쪽 트랜잭션 안에서만 동작(`Propagation.MANDATORY`), 잠금 실패 500 `INVENTORY_UPDATE_FAILED` |
| `MarginCalculator.marginRate(salesAmount, marginAmount)` | 납품 완료 시 매출 생성, 마진 집계, 대시보드 | 마진 ÷ 매출 × 100, 소수 2자리 반올림 (매출 0이면 0.00) |

여러 도메인 테이블을 직접 읽는 곳도 있습니다. `NativePartnerTransactionReader`(거래처 비활성화 확인, 거래 이력)는 `orders`, `deliveries`, `sales`, `purchases`를 native query로, `DashboardQueryRepository`는 주문·납품·매출·매입·재고 엔티티를 JPQL로 조회합니다. 이 테이블의 컬럼명이나 상태 값(`PENDING`, `WAITING`, `SHIPPED` 등)을 바꿀 때는 두 클래스도 함께 확인해주세요.

### Postman 컬렉션

`postman/` 폴더에 도메인별 컬렉션이 있습니다.

`PharmLink-auth`, `PharmLink-user`, `PharmLink-partner`, `PharmLink-company`, `PharmLink-order`, `PharmLink-delivery`, `PharmLink-purchase`, `PharmLink-sales`, `PharmLink-dashboard`

- 관리자 아이디·비밀번호(`admin_username`, `admin_password`)는 컬렉션 파일에 넣지 말고 Postman Environment에 넣어주세요. `postman/environments/`는 `.gitignore` 처리되어 있습니다.
- dev에는 상품 등록 API가 없어서 주문·납품·매입·매출·대시보드 컬렉션은 `items` 테이블에 상품을 직접 넣고 컬렉션 변수(`item_id_1` 등)를 채운 뒤 실행합니다. 예시 SQL은 `PharmLink-order`, `PharmLink-purchase` 컬렉션 설명에 있습니다.
- Postman 앱이 자동으로 만드는 `postman/collections/`도 `.gitignore` 처리되어 있습니다. 공유용 `postman/*.postman_collection.json`만 커밋해주세요.

## 브랜치 전략

- `main`: 배포용 (직접 커밋 금지)
- `dev`: 개발 통합 브랜치
- `feat/{기능명}`: 기능 개발 브랜치 (예: `feat/auth`, `feat/order`, `feat/sales`)
- `fix/{내용}`, `test/{내용}`: 수정·테스트 보강 브랜치 (예: `fix/s1-s3-review`, `test/auth-partner`)

작업 완료 후 `dev` 브랜치로 Pull Request를 올려서 팀원 리뷰 후 머지합니다.

## 주의사항

- `application-secret.yml`, `.env`에 들어가는 비밀번호/키 값은 절대 커밋하지 마세요.
- 도메인 패키지 하위 폴더 이름(`controller`, `service`, `repository`, `entity`, `dto`)은 임의로 바꾸지 말고 통일해주세요.
- `docker-compose.yml`, `container_name` 등 공용 설정 파일을 로컬에서 임의로 수정하고 커밋하지 마세요. 변경이 필요하면 팀 채팅방에 먼저 공유해주세요.
