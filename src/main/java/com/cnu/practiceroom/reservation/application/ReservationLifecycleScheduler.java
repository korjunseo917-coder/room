package com.cnu.practiceroom.reservation.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class ReservationLifecycleScheduler {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private final ReservationLifecycleService lifecycleService;

    public ReservationLifecycleScheduler(
            ReservationLifecycleService lifecycleService
    ) {
        this.lifecycleService = lifecycleService;
    }

    /*
     * 매분 0초에 실행한다.
     * 서버가 잠시 중단되었다가 다시 실행되더라도
     * 경계를 지난 예약을 다음 실행에서 함께 처리한다.
     */
    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Seoul"
    )
    public void updateReservationStatuses() {
        lifecycleService.process(
                ZonedDateTime.now(SEOUL)
        );
    }
}
