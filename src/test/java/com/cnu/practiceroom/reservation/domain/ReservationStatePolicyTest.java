package com.cnu.practiceroom.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationStatePolicyTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private final ReservationStatePolicy policy =
            new ReservationStatePolicy();

    @Test
    @DisplayName("신규 예약의 최초 상태는 승인 대기다")
    void createsReservationAsPending() {
        assertEquals(
                ReservationStatus.PENDING,
                policy.initialStatus()
        );
    }

    @Test
    @DisplayName("이용 시작 전 승인 대기 예약을 승인할 수 있다")
    void approvesPendingReservationBeforeStart() {
        ReservationStatus result = policy.approve(
                ReservationStatus.PENDING,
                at(17),
                at(18)
        );

        assertEquals(
                ReservationStatus.APPROVED,
                result
        );
    }

    @Test
    @DisplayName("이용 시작 정각에는 승인할 수 없다")
    void rejectsApprovalExactlyAtStart() {
        assertThrows(
                IllegalStateException.class,
                () -> policy.approve(
                        ReservationStatus.PENDING,
                        at(18),
                        at(18)
                )
        );
    }

    @Test
    @DisplayName("이용 시작 전 승인 대기 예약을 거절할 수 있다")
    void rejectsPendingReservationBeforeStart() {
        ReservationStatus result = policy.reject(
                ReservationStatus.PENDING,
                at(17),
                at(18)
        );

        assertEquals(
                ReservationStatus.REJECTED,
                result
        );
    }

    @Test
    @DisplayName("신청자는 승인 대기 예약을 시작 전에 취소할 수 있다")
    void requesterCancelsPendingReservation() {
        ReservationStatus result =
                policy.cancelByRequester(
                        ReservationStatus.PENDING,
                        at(17),
                        at(18)
                );

        assertEquals(
                ReservationStatus.CANCELED,
                result
        );
    }

    @Test
    @DisplayName("신청자는 승인된 예약을 시작 전에 취소할 수 있다")
    void requesterCancelsApprovedReservation() {
        ReservationStatus result =
                policy.cancelByRequester(
                        ReservationStatus.APPROVED,
                        at(17),
                        at(18)
                );

        assertEquals(
                ReservationStatus.CANCELED,
                result
        );
    }

    @Test
    @DisplayName("신청자는 밀려난 대기예약의 자동복구를 포기할 수 있다")
    void requesterCancelsDisplacedReservation() {
        ReservationStatus result =
                policy.cancelByRequester(
                        ReservationStatus.DISPLACED,
                        at(17),
                        at(18)
                );

        assertEquals(
                ReservationStatus.CANCELED,
                result
        );
    }

    @Test
    @DisplayName("이용 시작 정각에는 신청자가 취소할 수 없다")
    void rejectsRequesterCancellationExactlyAtStart() {
        assertThrows(
                IllegalStateException.class,
                () -> policy.cancelByRequester(
                        ReservationStatus.APPROVED,
                        at(18),
                        at(18)
                )
        );
    }

    @Test
    @DisplayName("승인 대기 대기예약은 정규예약에 의해 밀려날 수 있다")
    void displacesPendingStandbyReservation() {
        ReservationStatus result =
                policy.displaceStandby(
                        ReservationType.STANDBY,
                        ReservationStatus.PENDING
                );

        assertEquals(
                ReservationStatus.DISPLACED,
                result
        );
    }

    @Test
    @DisplayName("승인된 대기예약도 정규예약에 의해 밀려날 수 있다")
    void displacesApprovedStandbyReservation() {
        ReservationStatus result =
                policy.displaceStandby(
                        ReservationType.STANDBY,
                        ReservationStatus.APPROVED
                );

        assertEquals(
                ReservationStatus.DISPLACED,
                result
        );
    }

    @Test
    @DisplayName("정규예약은 대기예약처럼 밀려날 수 없다")
    void rejectsDisplacingRegularReservation() {
        assertThrows(
                IllegalStateException.class,
                () -> policy.displaceStandby(
                        ReservationType.REGULAR,
                        ReservationStatus.PENDING
                )
        );
    }

    @Test
    @DisplayName("조건을 충족한 밀려난 예약은 승인 대기로 복구한다")
    void restoresDisplacedReservation() {
        ReservationStatus result =
                policy.restoreDisplaced(
                        ReservationStatus.DISPLACED
                );

        assertEquals(
                ReservationStatus.PENDING,
                result
        );
    }

    @Test
    @DisplayName("승인 대기 예약은 이용 시작 정각에 만료된다")
    void expiresPendingReservationAtStart() {
        ReservationStatus result =
                policy.expire(
                        ReservationStatus.PENDING,
                        at(18),
                        at(18)
                );

        assertEquals(
                ReservationStatus.EXPIRED,
                result
        );
    }

    @Test
    @DisplayName("이용 시작 전에는 예약을 만료할 수 없다")
    void rejectsExpirationBeforeStart() {
        assertThrows(
                IllegalStateException.class,
                () -> policy.expire(
                        ReservationStatus.PENDING,
                        at(17),
                        at(18)
                )
        );
    }

    @Test
    @DisplayName("승인된 예약은 이용 종료 정각에 완료된다")
    void completesApprovedReservationAtEnd() {
        ReservationStatus result =
                policy.complete(
                        ReservationStatus.APPROVED,
                        at(20),
                        at(20)
                );

        assertEquals(
                ReservationStatus.COMPLETED,
                result
        );
    }

    @Test
    @DisplayName("이용 종료 전에는 완료 처리할 수 없다")
    void rejectsCompletionBeforeEnd() {
        assertThrows(
                IllegalStateException.class,
                () -> policy.complete(
                        ReservationStatus.APPROVED,
                        at(19),
                        at(20)
                )
        );
    }

    @Test
    @DisplayName("관리자가 이용 시작 전에 취소하면 취소 상태가 된다")
    void adminCancelsReservationBeforeStart() {
        ReservationStatus result =
                policy.cancelOrVoidByAdmin(
                        ReservationStatus.APPROVED,
                        at(17),
                        at(18)
                );

        assertEquals(
                ReservationStatus.CANCELED,
                result
        );
    }

    @Test
    @DisplayName("관리자가 이용 시작 후 승인된 예약을 정정하면 무효 상태가 된다")
    void adminVoidsReservationAfterStart() {
        ReservationStatus result =
                policy.cancelOrVoidByAdmin(
                        ReservationStatus.APPROVED,
                        at(19),
                        at(18)
                );

        assertEquals(
                ReservationStatus.VOIDED,
                result
        );
    }

    @Test
    @DisplayName("완료된 예약은 관리자만 사후 무효 처리할 수 있다")
    void adminVoidsCompletedReservation() {
        ReservationStatus result =
                policy.cancelOrVoidByAdmin(
                        ReservationStatus.COMPLETED,
                        at(21),
                        at(18)
                );

        assertEquals(
                ReservationStatus.VOIDED,
                result
        );
    }

    @Test
    @DisplayName("거절된 예약을 승인할 수 없다")
    void rejectsApprovingRejectedReservation() {
        assertThrows(
                IllegalStateException.class,
                () -> policy.approve(
                        ReservationStatus.REJECTED,
                        at(17),
                        at(18)
                )
        );
    }

    private ZonedDateTime at(int hour) {
        return ZonedDateTime.of(
                2026,
                9,
                15,
                hour,
                0,
                0,
                0,
                SEOUL
        );
    }
}