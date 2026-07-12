INSERT INTO orders (id, order_no, user_id, total_amount, status)
VALUES (1, 'ORD20260711001', 1, 298.00, 1);

INSERT INTO orders (id, order_no, user_id, total_amount, status)
VALUES (2, 'ORD20260711002', 2, 99.00, 2);

INSERT INTO order_item (id, order_id, product_id, product_name, product_price, quantity, amount)
VALUES (1, 1, 1, 'Keyboard', 199.00, 1, 199.00);

INSERT INTO order_item (id, order_id, product_id, product_name, product_price, quantity, amount)
VALUES (2, 1, 2, 'Mouse', 99.00, 1, 99.00);

INSERT INTO order_item (id, order_id, product_id, product_name, product_price, quantity, amount)
VALUES (3, 2, 2, 'Mouse', 99.00, 1, 99.00);
