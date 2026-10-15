-- 판매 상품 교체
-- 기존 상품은 지난 결제가 참조하므로 지우지 않고 판매만 중지한다
UPDATE shop_product SET active = FALSE WHERE active = TRUE;

INSERT INTO shop_product (name, gem_amount, bonus_gem_amount, price, sale_price, active, created_at) VALUES
    ('보석 2개 구매',   2, 0,  1000,  NULL, TRUE, now()),
    ('보석 10개 구매', 10, 0,  5000,  NULL, TRUE, now()),
    ('보석 20개 구매', 20, 0, 10000,  9000, TRUE, now()),
    ('보석 30개 구매', 30, 0, 15000, 13000, TRUE, now()),
    ('보석 50개 구매', 50, 0, 25000, 21000, TRUE, now());
