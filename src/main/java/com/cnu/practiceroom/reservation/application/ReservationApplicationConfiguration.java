package com.cnu.practiceroom.reservation.application;

import com.cnu.practiceroom.reservation.domain.MonthlyQuotaPolicy;
import com.cnu.practiceroom.reservation.domain.ReservationPolicy;
import com.cnu.practiceroom.reservation.domain.ReservationPolicySettings;
import com.cnu.practiceroom.reservation.domain.ReservationPriorityPolicy;
import com.cnu.practiceroom.reservation.domain.ReservationStatePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReservationApplicationConfiguration {

    @Bean
    ReservationPolicySettings reservationPolicySettings() {
        /*
         * 현재는 확정된 기본 설정을 사용한다.
         * 관리자 정책 설정 기능을 만들 때 이 Bean만
         * DB에서 현재 설정을 읽도록 교체할 수 있다.
         */
        return ReservationPolicySettings.standard();
    }

    @Bean
    ReservationPolicy reservationPolicy(
            ReservationPolicySettings settings
    ) {
        return new ReservationPolicy(settings);
    }

    @Bean
    MonthlyQuotaPolicy monthlyQuotaPolicy() {
        return new MonthlyQuotaPolicy();
    }

    @Bean
    ReservationPriorityPolicy reservationPriorityPolicy() {
        return new ReservationPriorityPolicy();
    }

    @Bean
    ReservationStatePolicy reservationStatePolicy() {
        return new ReservationStatePolicy();
    }
}
