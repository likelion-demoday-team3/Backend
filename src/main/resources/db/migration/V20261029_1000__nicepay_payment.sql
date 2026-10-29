-- 결제사를 카카오페이에서 나이스페이로 변경. 테이블 구조는 그대로 쓰고 설명만 바꾼다
COMMENT ON TABLE payment IS '보석 구매 결제 (나이스페이)';
COMMENT ON COLUMN payment.order_id IS '우리가 만든 주문 번호 (나이스페이 orderId)';
COMMENT ON COLUMN payment.payment_key IS '나이스페이 거래 키 (tid). 결제창 인증 후 승인할 때 저장';
