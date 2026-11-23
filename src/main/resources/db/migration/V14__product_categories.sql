CREATE TABLE product_categories (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    name_key VARCHAR(64) NOT NULL,
    sort_order BIGINT NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_product_category_name UNIQUE (name_key)
);

INSERT INTO product_categories (id, name, name_key, sort_order, visible) VALUES
    (1, 'Mugs', 'bb6e5531e24ede7501ac6041c8ec5e9b313961c8654efdb38f00e8c4e7cd16e4', 0, TRUE),
    (2, 'Tableware', 'f961bed166d02a293bd0e64cdfed958b733e7b1ec4b5cb0a2e5fa037daacdcc2', 1, TRUE),
    (3, 'One Piece', 'c652649297eb6abdec4298e24f7b6f78125741e36eb9b76f7708481a910daf7f', 2, TRUE);

ALTER TABLE products ADD COLUMN category_id BIGINT NULL;
ALTER TABLE products ADD CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES product_categories(id);
CREATE INDEX idx_products_category_date ON products (category_id, created_at, id);
CREATE INDEX idx_products_date ON products (created_at, id);
