#dependencies

Spring web
Validation
Lombok
Spring Data JPA
PostgreSQL Driver
WebSocket
H2 Database
Java Mail Sender

#의존성 추가설명

*Java Mail Sender
학교 이메일 인증 기능
아래는 코드 예시

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    public void sendVerificationCode(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("이메일 인증번호");
        message.setText("인증번호는 " + code + "입니다.");

        javaMailSender.send(message);
    }
}

*WebSocket
offer / answer / ICE candidate 전달
매칭된 두 사용자에게 서로 연결 정보 전달
라운드 시작/종료 이벤트 전송
