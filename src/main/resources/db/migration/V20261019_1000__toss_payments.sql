-- 결제사를 카카오페이에서 토스페이먼츠 직접 연동으로 변경
-- 이미 저장된 카카오페이 결제(tid)는 토스로 환불할 수 없다
COMMENT ON TABLE payment IS '보석 구매 결제 (토스페이먼츠)';
COMMENT ON COLUMN payment.order_id IS '우리가 만든 주문 번호 (토스 orderId)';
COMMENT ON COLUMN payment.payment_key IS '토스 결제 고유 번호 (paymentKey). 승인 성공 시 저장';
