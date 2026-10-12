package com.example.kiwi_backend.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 사용 설정 (세션 30분 전 판단, A의 로비 마감·1라운드 시작 주기 조회 등).
 *
 * app.scheduling.enabled=false 로 끌 수 있다. 통합 테스트에서 스케줄러가 테스트 데이터를
 * 마음대로 처리하지 않도록 끄기 위한 용도. 설정이 없으면 켜진다.
 *
 * A의 TaskSchedulerConfig(풀 크기 2)를 함께 쓴다.
 * A의 TaskSchedulerConfig에 @EnableScheduling을 따로 붙이지 말 것 (이 설정으로 끄는 게 안 먹힘).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
