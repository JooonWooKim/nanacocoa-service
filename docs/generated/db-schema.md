# 자동 생성 데이터베이스 스키마 이력

상태: 자동 생성
원본: `src/main/resources/db/migration` 아래 순서가 지정된 SQL 파일
재생성: `./scripts/harness generate-docs`

이 파일을 직접 수정하지 않습니다. 새 Flyway 마이그레이션을 추가하고 재생성합니다.

아래 SQL은 버전 관리되는 운영 스키마 이력이며 실행 중인 데이터베이스 dump가 아닙니다.


## `src/main/resources/db/migration/V1__baseline_members_and_products.sql`

```sql
CREATE TABLE IF NOT EXISTS members (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL,
  name VARCHAR(100) NOT NULL,
  password VARCHAR(100) NOT NULL,
  is_admin BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_members_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS products (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  price BIGINT NOT NULL,
  summary VARCHAR(500) NOT NULL,
  detail_title VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  image_url VARCHAR(500),
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_products_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## `src/main/resources/db/migration/V2__create_orders_and_payments.sql`

```sql
CREATE TABLE orders (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id VARCHAR(64) NOT NULL,
  member_id BIGINT NOT NULL,
  total_amount BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_orders_order_id UNIQUE (order_id),
  CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES members (id),
  INDEX idx_orders_member_created (member_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(255) NOT NULL,
  unit_price BIGINT NOT NULL,
  quantity INT NOT NULL,
  line_amount BIGINT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
  INDEX idx_order_items_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  payment_key VARCHAR(200) NOT NULL,
  approval_idempotency_key VARCHAR(300) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  compensation_idempotency_key VARCHAR(300) NOT NULL,
  amount BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  pg_status VARCHAR(50),
  pg_method VARCHAR(50),
  failure_code VARCHAR(100),
  failure_message VARCHAR(500),
  recovery_action VARCHAR(32) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(6),
  approved_at DATETIME(6),
  canceled_at DATETIME(6),
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_payments_payment_key UNIQUE (payment_key),
  CONSTRAINT uk_payments_approval_idem UNIQUE (approval_idempotency_key),
  CONSTRAINT uk_payments_compensation_idem UNIQUE (compensation_idempotency_key),
  CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
  INDEX idx_payments_order_status (order_id, status),
  INDEX idx_payments_recovery (recovery_action, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_cancellations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  payment_id BIGINT NOT NULL,
  idempotency_key VARCHAR(300) NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  reason VARCHAR(200) NOT NULL,
  status VARCHAR(32) NOT NULL,
  pg_transaction_key VARCHAR(64),
  failure_code VARCHAR(100),
  failure_message VARCHAR(500),
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(6),
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_payment_cancellations_idem UNIQUE (idempotency_key),
  CONSTRAINT uk_payment_cancellations_tx UNIQUE (pg_transaction_key),
  CONSTRAINT fk_payment_cancellations_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
  INDEX idx_payment_cancellations_payment_status (payment_id, status),
  INDEX idx_payment_cancellations_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## `src/main/resources/db/migration/V3__add_order_shipping_information.sql`

```sql
ALTER TABLE orders
  ADD COLUMN recipient_name VARCHAR(100) NOT NULL DEFAULT '';

ALTER TABLE orders
  ADD COLUMN phone_number VARCHAR(30) NOT NULL DEFAULT '';

ALTER TABLE orders
  ADD COLUMN shipping_address VARCHAR(500) NOT NULL DEFAULT '';

ALTER TABLE orders
  ADD COLUMN customer_request VARCHAR(500);

UPDATE orders
SET recipient_name = COALESCE(
  (SELECT members.name FROM members WHERE members.id = orders.member_id),
  ''
)
WHERE recipient_name = '';
```
