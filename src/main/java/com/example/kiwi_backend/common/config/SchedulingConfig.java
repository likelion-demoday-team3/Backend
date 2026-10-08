package com.example.kiwi_backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 사용 설정 (세션 30분 전 판단 등).
 *
 * 주의: A의 TaskSchedulerConfig에 이미 @EnableScheduling이 있으면 이 클래스는 지워도 된다 (중복돼도 동작은 같음).
 * @Scheduled 작업과 A의 라운드 타이머가 같은 TaskScheduler를 쓰므로, 그 스레드 풀 크기가 2 이상인지 확인할 것.
 * 1이면 세션 판단 작업이 도는 동안 라운드 타이머가 밀린다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
