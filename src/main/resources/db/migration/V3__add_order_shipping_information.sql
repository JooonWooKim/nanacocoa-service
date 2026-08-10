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
