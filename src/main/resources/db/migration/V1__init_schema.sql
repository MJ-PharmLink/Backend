-- =====================================================================
-- V1__init_schema.sql
-- 도메인 명세서(DB 설계서) 3장 기준 16개 테이블 생성
-- MySQL 8.x / utf8mb4 / utf8mb4_unicode_ci
-- Enum 값은 DB ENUM 대신 VARCHAR + 애플리케이션 검증
-- FK는 refresh_tokens만 ON DELETE CASCADE, 나머지는 RESTRICT (소프트 삭제 정책)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 3.1 users — 사용자
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id    BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_users_username UNIQUE (username),
    INDEX idx_users_role_active (role, is_active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.2 refresh_tokens — 리프레시 토큰 (사용자당 1건, SHA-256 해시만 저장)
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    token_id   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked_at TIMESTAMP    NULL     DEFAULT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (token_id),
    CONSTRAINT uk_rt_user_id UNIQUE (user_id),
    CONSTRAINT uk_rt_token_hash UNIQUE (token_hash),
    INDEX idx_rt_expires (expires_at),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.3 company — 회사 정보 (단일 행, company_id = 1)
-- ---------------------------------------------------------------------
CREATE TABLE company (
    company_id               BIGINT       NOT NULL DEFAULT 1,
    name                     VARCHAR(100) NOT NULL,
    business_number          VARCHAR(12)  NOT NULL,
    representative_name      VARCHAR(50)  NOT NULL,
    wholesale_license_number VARCHAR(50)  NULL     DEFAULT NULL,
    address                  VARCHAR(255) NOT NULL,
    phone                    VARCHAR(20)  NOT NULL,
    fax                      VARCHAR(20)  NULL     DEFAULT NULL,
    email                    VARCHAR(100) NULL     DEFAULT NULL,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (company_id),
    CONSTRAINT chk_company_single_row CHECK (company_id = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.4 business_partners — 거래처 (CUSTOMER / SUPPLIER)
-- ---------------------------------------------------------------------
CREATE TABLE business_partners (
    partner_id      BIGINT       NOT NULL AUTO_INCREMENT,
    partner_type    VARCHAR(20)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    business_number VARCHAR(12)  NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    address         VARCHAR(255) NOT NULL,
    manager_name    VARCHAR(50)  NULL     DEFAULT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (partner_id),
    CONSTRAINT uk_bp_business_number UNIQUE (business_number),
    INDEX idx_bp_type_active (partner_type, is_active),
    INDEX idx_bp_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.5 categories — 카테고리
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    category_id   BIGINT       NOT NULL AUTO_INCREMENT,
    category_name VARCHAR(50)  NOT NULL,
    description   VARCHAR(200) NULL     DEFAULT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (category_id),
    CONSTRAINT uk_categories_name UNIQUE (category_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.6 warehouses — 창고 (is_default = true 1건은 애플리케이션에서 보장)
-- ---------------------------------------------------------------------
CREATE TABLE warehouses (
    warehouse_id BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(50)  NOT NULL,
    location     VARCHAR(255) NULL     DEFAULT NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (warehouse_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.7 items — 상품(의약품) 마스터
-- ---------------------------------------------------------------------
CREATE TABLE items (
    item_id      BIGINT       NOT NULL AUTO_INCREMENT,
    item_code    VARCHAR(20)  NOT NULL,
    item_name    VARCHAR(100) NOT NULL,
    category_id  BIGINT       NOT NULL,
    spec         VARCHAR(100) NULL     DEFAULT NULL,
    unit         VARCHAR(20)  NOT NULL,
    unit_cost    BIGINT       NOT NULL,
    unit_price   BIGINT       NOT NULL,
    safety_stock INT          NOT NULL DEFAULT 0,
    supplier_id  BIGINT       NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (item_id),
    CONSTRAINT uk_items_item_code UNIQUE (item_code),
    CONSTRAINT chk_items_amounts CHECK (unit_cost >= 0 AND unit_price >= 0 AND safety_stock >= 0),
    INDEX idx_items_category (category_id),
    INDEX idx_items_supplier (supplier_id),
    INDEX idx_items_name (item_name),
    CONSTRAINT fk_items_category FOREIGN KEY (category_id) REFERENCES categories (category_id),
    CONSTRAINT fk_items_supplier FOREIGN KEY (supplier_id) REFERENCES business_partners (partner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.8 inventories — 재고 (창고 × 상품)
-- ---------------------------------------------------------------------
CREATE TABLE inventories (
    inventory_id BIGINT    NOT NULL AUTO_INCREMENT,
    warehouse_id BIGINT    NOT NULL,
    item_id      BIGINT    NOT NULL,
    quantity     INT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (inventory_id),
    CONSTRAINT uk_inv_warehouse_item UNIQUE (warehouse_id, item_id),
    CONSTRAINT chk_inv_quantity CHECK (quantity >= 0),
    INDEX idx_inv_item (item_id),
    CONSTRAINT fk_inv_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (warehouse_id),
    CONSTRAINT fk_inv_item FOREIGN KEY (item_id) REFERENCES items (item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.9 inventory_lots — 재고 로트 (유통기한 단위)
-- ---------------------------------------------------------------------
CREATE TABLE inventory_lots (
    lot_id       BIGINT      NOT NULL AUTO_INCREMENT,
    inventory_id BIGINT      NOT NULL,
    lot_number   VARCHAR(50) NOT NULL,
    expiry_date  DATE        NOT NULL,
    quantity     INT         NOT NULL DEFAULT 0,
    received_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (lot_id),
    CONSTRAINT uk_lot_inventory_lot_number UNIQUE (inventory_id, lot_number),
    CONSTRAINT chk_lot_quantity CHECK (quantity >= 0),
    INDEX idx_lot_expiry (expiry_date),
    INDEX idx_lot_inv_expiry (inventory_id, expiry_date),
    CONSTRAINT fk_lot_inventory FOREIGN KEY (inventory_id) REFERENCES inventories (inventory_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.10 inventory_transactions — 재고 이력 (INSERT ONLY)
-- reference_id는 다형 참조라 FK 없음 (PURCHASE → purchase_id / ORDER → order_id / ADJUSTMENT → NULL)
-- ---------------------------------------------------------------------
CREATE TABLE inventory_transactions (
    transaction_id BIGINT       NOT NULL AUTO_INCREMENT,
    type           VARCHAR(20)  NOT NULL,
    reference_type VARCHAR(20)  NOT NULL,
    reference_id   BIGINT       NULL     DEFAULT NULL,
    warehouse_id   BIGINT       NOT NULL,
    item_id        BIGINT       NOT NULL,
    lot_id         BIGINT       NOT NULL,
    quantity       INT          NOT NULL,
    quantity_after INT          NOT NULL,
    reason         VARCHAR(30)  NULL     DEFAULT NULL,
    memo           VARCHAR(200) NULL     DEFAULT NULL,
    created_by     BIGINT       NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (transaction_id),
    CONSTRAINT chk_tx_quantity CHECK (quantity > 0),
    CONSTRAINT chk_tx_reference CHECK ((reference_type = 'ADJUSTMENT') = (reference_id IS NULL)),
    INDEX idx_tx_type (type),
    INDEX idx_tx_item_created (item_id, created_at DESC),
    INDEX idx_tx_wh_created (warehouse_id, created_at DESC),
    INDEX idx_tx_ref (reference_type, reference_id),
    INDEX idx_tx_created (created_at DESC),
    CONSTRAINT fk_tx_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (warehouse_id),
    CONSTRAINT fk_tx_item FOREIGN KEY (item_id) REFERENCES items (item_id),
    CONSTRAINT fk_tx_lot FOREIGN KEY (lot_id) REFERENCES inventory_lots (lot_id),
    CONSTRAINT fk_tx_created_by FOREIGN KEY (created_by) REFERENCES users (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.11 orders — 주문 (PENDING → APPROVED / CANCELLED)
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    order_id      BIGINT       NOT NULL AUTO_INCREMENT,
    order_number  VARCHAR(20)  NOT NULL,
    partner_id    BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    total_amount  BIGINT       NOT NULL DEFAULT 0,
    cancel_reason VARCHAR(200) NULL     DEFAULT NULL,
    approved_at   TIMESTAMP    NULL     DEFAULT NULL,
    cancelled_at  TIMESTAMP    NULL     DEFAULT NULL,
    cancelled_by  BIGINT       NULL     DEFAULT NULL,
    created_by    BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    INDEX idx_orders_status_created (status, created_at DESC),
    INDEX idx_orders_partner_created (partner_id, created_at DESC),
    CONSTRAINT fk_orders_partner FOREIGN KEY (partner_id) REFERENCES business_partners (partner_id),
    CONSTRAINT fk_orders_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users (user_id),
    CONSTRAINT fk_orders_created_by FOREIGN KEY (created_by) REFERENCES users (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.12 order_items — 주문 라인 (단가·원가 스냅샷)
-- ---------------------------------------------------------------------
CREATE TABLE order_items (
    order_item_id BIGINT NOT NULL AUTO_INCREMENT,
    order_id      BIGINT NOT NULL,
    item_id       BIGINT NOT NULL,
    quantity      INT    NOT NULL,
    unit_price    BIGINT NOT NULL,
    unit_cost     BIGINT NOT NULL,
    line_amount   BIGINT NOT NULL,
    PRIMARY KEY (order_item_id),
    CONSTRAINT uk_oi_order_item UNIQUE (order_id, item_id),
    CONSTRAINT chk_oi_quantity CHECK (quantity >= 1),
    INDEX idx_oi_item (item_id),
    CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_oi_item FOREIGN KEY (item_id) REFERENCES items (item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.13 deliveries — 납품 (주문과 1:1, WAITING → SHIPPED → DELIVERED)
-- ---------------------------------------------------------------------
CREATE TABLE deliveries (
    delivery_id  BIGINT      NOT NULL AUTO_INCREMENT,
    order_id     BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    shipped_at   TIMESTAMP   NULL     DEFAULT NULL,
    delivered_at TIMESTAMP   NULL     DEFAULT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (delivery_id),
    CONSTRAINT uk_del_order UNIQUE (order_id),
    INDEX idx_del_status_created (status, created_at DESC),
    CONSTRAINT fk_del_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.14 purchases — 매입 헤더 (수정·삭제 API 없음)
-- ---------------------------------------------------------------------
CREATE TABLE purchases (
    purchase_id   BIGINT    NOT NULL AUTO_INCREMENT,
    partner_id    BIGINT    NOT NULL,
    warehouse_id  BIGINT    NOT NULL,
    purchase_date DATE      NOT NULL,
    total_amount  BIGINT    NOT NULL DEFAULT 0,
    created_by    BIGINT    NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (purchase_id),
    INDEX idx_pur_date (purchase_date),
    INDEX idx_pur_partner_date (partner_id, purchase_date DESC),
    INDEX idx_pur_wh_date (warehouse_id, purchase_date DESC),
    CONSTRAINT fk_pur_partner FOREIGN KEY (partner_id) REFERENCES business_partners (partner_id),
    CONSTRAINT fk_pur_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (warehouse_id),
    CONSTRAINT fk_pur_created_by FOREIGN KEY (created_by) REFERENCES users (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.15 purchase_items — 매입 라인 (로트 연결, 원가 스냅샷)
-- ---------------------------------------------------------------------
CREATE TABLE purchase_items (
    purchase_item_id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_id      BIGINT NOT NULL,
    item_id          BIGINT NOT NULL,
    lot_id           BIGINT NOT NULL,
    quantity         INT    NOT NULL,
    unit_cost        BIGINT NOT NULL,
    line_amount      BIGINT NOT NULL,
    PRIMARY KEY (purchase_item_id),
    CONSTRAINT uk_pi_purchase_lot UNIQUE (purchase_id, lot_id),
    CONSTRAINT chk_pi_quantity CHECK (quantity >= 1),
    INDEX idx_pi_item (item_id),
    INDEX idx_pi_lot (lot_id),
    CONSTRAINT fk_pi_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (purchase_id),
    CONSTRAINT fk_pi_item FOREIGN KEY (item_id) REFERENCES items (item_id),
    CONSTRAINT fk_pi_lot FOREIGN KEY (lot_id) REFERENCES inventory_lots (lot_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3.16 sales — 매출/마진 (납품 완료 시에만 INSERT)
-- ---------------------------------------------------------------------
CREATE TABLE sales (
    sale_id       BIGINT        NOT NULL AUTO_INCREMENT,
    order_id      BIGINT        NOT NULL,
    delivery_id   BIGINT        NOT NULL,
    partner_id    BIGINT        NOT NULL,
    sales_amount  BIGINT        NOT NULL,
    cost_amount   BIGINT        NOT NULL,
    margin_amount BIGINT        NOT NULL,
    margin_rate   DECIMAL(10,2) NOT NULL,
    sale_date     DATE          NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (sale_id),
    CONSTRAINT uk_sales_order UNIQUE (order_id),
    CONSTRAINT uk_sales_delivery UNIQUE (delivery_id),
    INDEX idx_sales_date (sale_date),
    INDEX idx_sales_partner_date (partner_id, sale_date),
    CONSTRAINT fk_sales_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_sales_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries (delivery_id),
    CONSTRAINT fk_sales_partner FOREIGN KEY (partner_id) REFERENCES business_partners (partner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
