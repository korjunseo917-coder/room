package com.cnu.practiceroom.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationPriorityPolicyTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private final ReservationPriorityPolicy policy =
            new ReservationPriorityPolicy();

    @Test
    @DisplayName("겹치는 예약이 없으면 신규 예약을 허용한다")
    void allowsRequestWhenNoConflictExists() {
        ReservationSnapshot request =
                reservation(
                        10, 100, 321,
                        ReservationType.STANDBY,
                        ReservationStatus.PENDING,
                        18, 20
                );

        ReservationPriorityDecision result =
                policy.decideNewRequest(
                        request,
                        List.of()
                );

        assertEquals(
                ReservationPriorityAction.ALLOW,
                result.action()
        );
    }

    @Test
    @DisplayName("대기예약이 있는 시간에 신규 대기예약을 거절한다")
    void rejectsStandbyWhenStandbyAlreadyExists() {
        ReservationSnapshot existing =
                reservation(
                        1, 100, 321,
                        ReservationType.STANDBY,
                        ReservationStatus.PENDING,
                        18, 20
                );

        ReservationSnapshot request =
                reservation(
                        2, 200, 321,
                        ReservationType.STANDBY,
                        ReservationStatus.PENDING,
                        19, 21
                );

        ReservationPriorityDecision result =
                policy.decideNewRequest(
                        request,
                        List.of(existing)
                );

        assertEquals(
                ReservationPriorityAction.REJECT,
                result.action()
        );
    }

    @Test
    @DisplayName("정규예약이 있는 시간에 신규 대기예약을 거절한다")
    void rejectsStandbyWhenRegularExists() {
        ReservationSnapshot existing =
                reservation(
                        1, 100, 321,
                        ReservationType.REGULAR,
                        ReservationStatus.APPROVED,
                        18, 20
                );

        ReservationSnapshot request =
                reservation(
                        2, 200, 321,
                        ReservationType.STANDBY,
                        ReservationStatus.PENDING,
                        19, 21
                );

        ReservationPriorityDecision result =
                policy.decideNewRequest(
                        request,
                        List.of(existing)
                );

        assertEquals(
                ReservationPriorityAction.REJECT,
                result.action()
        );
    }

    @Test
    @DisplayName("정규예약이 있는 시간에 신규 정규예약을 거절한다")
    void rejectsRegularWhenRegularAlreadyExists() {
        ReservationSnapshot existing =
                reservation(
                        1, 100, 321,
                        ReservationType.REGULAR,
                        ReservationStatus.PENDING,
                        18, 20
                );

        ReservationSnapshot request =
                reservation(
                        2, 200, 321,
                        ReservationType.REGULAR,
                        ReservationStatus.PENDING,
                        19, 21
                );

        ReservationPriorityDecision result =
                policy.decideNewRequest(
                        request,
                        List.of(existing)
                );

        assertEquals(
                ReservationPriorityAction.REJECT,
                result.action()
        );
    }

    @Test
    @DisplayName("신규 정규예약은 승인 대기 대기예약을 밀어낸다")
    void regularDisplacesPendingStandby() {
        ReservationSnapshot existing =
                reservation(
                        1, 100, 321,
                        ReservationType.STANDBY,
                        ReservationStatus.PENDING,
                        18, 20
                );

        ReservationSnapshot request =
                reservation(
                        2, 200, 321,
                        ReservationType.REGULAR,
                        ReservationStatus.PENDING,
                        19, 21
                );

        ReservationPriorityDecision result =
                policy.decideNewRequest(
                        request,
                        List.of(existing)
                );

        assertEquals(
                ReservationPriorityAction
                        .DISPLACE_EXISTING_STANDBY,
                result.action()
        );

        assertEquals(
                List.of(1L),
                result.displacedReservationIds()
        );
    }

    @Test
    @DisplayName("신규 정규예약은 승인된 대기예약도 밀어낸다")
    void regularDisplacesApprovedStandby() {
        ReservationSnapshot existing =
                reservation(
                        1, 100, 321,
                        ReservationType.STANDBY,
                        ReservationStatus.APPROVED,
                        18, 20
                );

        ReservationSnapshot request =
                reservation(
                        2, 200, 321,
                        ReservationType.REGULAR,
                        ReservationStatus.PENDING,
                        19, 21
                );

        ReservationPriorityDecision result =
                policy.decideNewRequest(
                        request,
                        List.of(existing)
                );

        assertEquals(
                ReservationPriorityAction
                        .DISPLACE_EXISTING_STANDBY,
                result.action()
        );
    }

    @Test
    @DisplayName("신규 정규예약은 겹치는 여러 대기예약을 모두 밀어낸다")
    void regularDisplacesMultipleStandbyReservations() {
        ReservationSnapshot first =
                reservation(
                        1, 100, 321,
                        ReservationType.STANDBY,
                        ReservationStatus.PENDING,
                        18, 19
                );

        ReservationSnapshot second =
                reservation(
                        2, 200, 321,
                        ReservationType.STANDBY,
                        ReservationStatus.APPROVED,
                        19, 20
                );

        ReservationSnapshot request =
                reservation(
                        3, 300, 321,
                        ReservationType.REGULAR,
                        ReservationStatus.PENDING,
                        18, 21
                );

        ReservationPriorityDecision result =
                policy.decideNewRequest(
                        request,
                        List.of(first, second)
                );

        assertEquals(
                List.of(1L, 2L),
                result.displacedReservationIds()
        );
    }

    @Test
    @DisplayName("종료 시각과 다음 예약 시작 시각이 같으면 겹치지 않는다")
    void allowsAdjacentReservation() {
        ReservationSnapshot existing =
                reservation(
                        1, 100, 321,
                        ReservationType.REGULAR,
                        ReservationStatus.APPROVED,
                        18, 20
                );

        ReservationSnapshot request =
                reservation(
                        2, 200, 321,
                        ReservationType.STANDBY,
                        ReservationStatus.PENDING,
                        20, 21
                );

        ReservationPriorityDecision result =
                policy.decideNewRequest(
                        request,
                        List.of(existing)
                );

        assertEquals(
                ReservationPriorityAction.ALLOW,
                result.action()
        );
    }

    @Test
    @DisplayName("취소된 예약은 신규 신청을 막지 않는다")
    void ignoresCanceledReservation() {
        ReservationSnapshot existing =
                reservation(
                        1, 100, 321,
                        ReservationType.REGULAR,
                        ReservationStatus.CANCELED,
                        18, 20
                );

        ReservationSnapshot request =
                reservation(
                        2, 200, 321,
                        ReservationType.STANDBY,
                        ReservationStatus.PENDING,
                        19, 21
                );

        ReservationPriorityDecision result =
                policy.decideNewRequest(
                        request,
                        List.of(existing)
                );

        assertEquals(
                ReservationPriorityAction.ALLOW,
                result.action()
        );
    }

    @Test
    @DisplayName("복구 조건을 모두 만족하면 밀려난 대기예약을 복구할 수 있다")
    void restoresWhenAllConditionsAreMet() {
        ReservationSnapshot displaced =
                displacedReservation();

        boolean result = policy.canRestore(
                displaced,
                List.of(),
                true,
                at(14, 21),
                at(14, 22)
        );

        assertTrue(result);
    }

    @Test
    @DisplayName("원래 시간 일부가 다른 예약에 선점되면 복구할 수 없다")
    void rejectsRestoreWhenRoomSlotIsOccupied() {
        ReservationSnapshot displaced =
                displacedReservation();

        ReservationSnapshot active =
                reservation(
                        2, 200, 321,
                        ReservationType.REGULAR,
                        ReservationStatus.APPROVED,
                        19, 20
                );

        boolean result = policy.canRestore(
                displaced,
                List.of(active),
                true,
                at(14, 21),
                at(14, 22)
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("신청자가 같은 시간에 다른 방을 예약했으면 복구할 수 없다")
    void rejectsRestoreWhenRequesterHasOtherRoom() {
        ReservationSnapshot displaced =
                displacedReservation();

        ReservationSnapshot otherRoom =
                reservation(
                        2, 100, 322,
                        ReservationType.REGULAR,
                        ReservationStatus.APPROVED,
                        19, 20
                );

        boolean result = policy.canRestore(
                displaced,
                List.of(otherRoom),
                true,
                at(14, 21),
                at(14, 22)
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("신청 마감 정각에는 밀려난 예약을 복구할 수 없다")
    void rejectsRestoreExactlyAtDeadline() {
        boolean result = policy.canRestore(
                displacedReservation(),
                List.of(),
                true,
                at(14, 22),
                at(14, 22)
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("비활성 동아리 회원의 예약은 복구할 수 없다")
    void rejectsRestoreForInactiveMember() {
        boolean result = policy.canRestore(
                displacedReservation(),
                List.of(),
                false,
                at(14, 21),
                at(14, 22)
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("신청자가 취소한 대기예약은 복구할 수 없다")
    void rejectsRestoreForCanceledReservation() {
        ReservationSnapshot canceled =
                reservation(
                        1, 100, 321,
                        ReservationType.STANDBY,
                        ReservationStatus.CANCELED,
                        18, 21
                );

        boolean result = policy.canRestore(
                canceled,
                List.of(),
                true,
                at(14, 21),
                at(14, 22)
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("취소된 정규예약은 대기예약 복구를 방해하지 않는다")
    void ignoresCanceledRegularDuringRestore() {
        ReservationSnapshot displaced =
                displacedReservation();

        ReservationSnapshot canceledRegular =
                reservation(
                        2, 200, 321,
                        ReservationType.REGULAR,
                        ReservationStatus.CANCELED,
                        19, 20
                );

        boolean result = policy.canRestore(
                displaced,
                List.of(canceledRegular),
                true,
                at(14, 21),
                at(14, 22)
        );

        assertTrue(result);
    }

    private ReservationSnapshot displacedReservation() {
        return reservation(
                1, 100, 321,
                ReservationType.STANDBY,
                ReservationStatus.DISPLACED,
                18, 21
        );
    }

    private ReservationSnapshot reservation(
            long id,
            long requesterId,
            long roomId,
            ReservationType type,
            ReservationStatus status,
            int startHour,
            int endHour
    ) {
        return new ReservationSnapshot(
                id,
                requesterId,
                roomId,
                type,
                status,
                at(15, startHour),
                at(15, endHour)
        );
    }

    private ZonedDateTime at(
            int day,
            int hour
    ) {
        return ZonedDateTime.of(
                2026,
                9,
                day,
                hour,
                0,
                0,
                0,
                SEOUL
        );
    }
}