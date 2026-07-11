CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL
);
