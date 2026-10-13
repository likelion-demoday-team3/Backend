-- 결제사를 포트원에서 카카오페이 직접 연동으로 변경
COMMENT ON TABLE payment IS '보석 구매 결제 (카카오페이)';
COMMENT ON COLUMN payment.order_id IS '우리가 만든 주문 번호 (카카오페이 partner_order_id)';
COMMENT ON COLUMN payment.payment_key IS '카카오페이 결제 고유 번호 (tid)';

-- 초기 판매 상품. 화면에는 상품명(보석 개수)만 보이고 가격은 카카오페이 결제창에서만 보인다
-- 가격을 바꿀 때는 기존 상품을 active = false로 내리고 새로 추가한다
INSERT INTO shop_product (name, gem_amount, bonus_gem_amount, price, active, created_at) VALUES
    ('보석 10개 구매', 10, 0, 1000, TRUE, now()),
    ('보석 20개 구매', 20, 0, 2000, TRUE, now()),
    ('보석 30개 구매', 30, 0, 3000, TRUE, now()),
    ('보석 50개 구매', 50, 0, 5000, TRUE, now());
