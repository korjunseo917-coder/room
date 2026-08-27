package com.cnu.practiceroom.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationRequestPriorityPolicyTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private final ReservationPriorityPolicy policy =
            new ReservationPriorityPolicy();

    @Test
    @DisplayName("빈 시간의 신규 예약 신청은 허용한다")
    void allowsRequestWithoutConflict() {
        ReservationRequestSnapshot request =
                request(ReservationType.STANDBY);

        ReservationPriorityDecision decision =
                policy.decideNewRequest(
                        request,
                        List.of()
                );

        assertThat(decision.action())
                .isEqualTo(
                        ReservationPriorityAction.ALLOW
                );
    }

    @Test
    @DisplayName("정규예약 신청은 겹치는 대기예약을 밀어낸다")
    void regularRequestDisplacesStandby() {
        ReservationRequestSnapshot request =
                request(ReservationType.REGULAR);

        ReservationSnapshot existingStandby =
                new ReservationSnapshot(
                        100L,
                        20L,
                        321L,
                        ReservationType.STANDBY,
                        ReservationStatus.PENDING,
                        time(10),
                        time(12)
                );

        ReservationPriorityDecision decision =
                policy.decideNewRequest(
                        request,
                        List.of(existingStandby)
                );

        assertThat(decision.action())
                .isEqualTo(
                        ReservationPriorityAction
                                .DISPLACE_EXISTING_STANDBY
                );

        assertThat(decision.displacedReservationIds())
                .containsExactly(100L);
    }

    @Test
    @DisplayName("신규 대기예약은 기존 활성예약과 겹치면 거절한다")
    void standbyRequestCannotDisplaceExistingReservation() {
        ReservationRequestSnapshot request =
                request(ReservationType.STANDBY);

        ReservationSnapshot existingRegular =
                new ReservationSnapshot(
                        100L,
                        20L,
                        321L,
                        ReservationType.REGULAR,
                        ReservationStatus.APPROVED,
                        time(10),
                        time(12)
                );

        ReservationPriorityDecision decision =
                policy.decideNewRequest(
                        request,
                        List.of(existingRegular)
                );

        assertThat(decision.action())
                .isEqualTo(
                        ReservationPriorityAction.REJECT
                );
    }

    private ReservationRequestSnapshot request(
            ReservationType type
    ) {
        return new ReservationRequestSnapshot(
                10L,
                321L,
                type,
                time(10),
                time(12)
        );
    }

    private ZonedDateTime time(int hour) {
        return ZonedDateTime.of(
                2026,
                10,
                10,
                hour,
                0,
                0,
                0,
                SEOUL
        );
    }
}
