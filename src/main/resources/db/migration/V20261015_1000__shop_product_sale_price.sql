-- 상품 할인 가격. null이면 할인 없음, 있으면 이 금액으로 결제된다
-- 할인 적용: UPDATE shop_product SET sale_price = 800 WHERE id = 1;
-- 할인 해제: UPDATE shop_product SET sale_price = NULL WHERE id = 1;
ALTER TABLE shop_product
    ADD COLUMN sale_price INT,
    ADD CONSTRAINT ck_shop_product_sale_price CHECK (sale_price > 0 AND sale_price < price);

COMMENT ON COLUMN shop_product.sale_price IS '할인 가격 (원), null이면 할인 없음';
