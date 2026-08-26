package com.cnu.practiceroom.reservation.domain;

import java.util.List;

public record ReservationPriorityDecision(
        ReservationPriorityAction action,
        List<Long> displacedReservationIds
) {

    public ReservationPriorityDecision {
        if (action == null) {
            throw new IllegalArgumentException(
                    "우선순위 판단 결과는 필수입니다."
            );
        }

        if (displacedReservationIds == null) {
            throw new IllegalArgumentException(
                    "밀려날 예약 목록은 필수입니다."
            );
        }

        displacedReservationIds =
                List.copyOf(displacedReservationIds);

        if (action
                == ReservationPriorityAction
                .DISPLACE_EXISTING_STANDBY
                && displacedReservationIds.isEmpty()) {

            throw new IllegalArgumentException(
                    "밀어낼 대기예약이 필요합니다."
            );
        }

        if (action
                != ReservationPriorityAction
                .DISPLACE_EXISTING_STANDBY
                && !displacedReservationIds.isEmpty()) {

            throw new IllegalArgumentException(
                    "밀어내기 결과가 아니면 예약 목록이 비어야 합니다."
            );
        }
    }

    public static ReservationPriorityDecision allow() {
        return new ReservationPriorityDecision(
                ReservationPriorityAction.ALLOW,
                List.of()
        );
    }

    public static ReservationPriorityDecision reject() {
        return new ReservationPriorityDecision(
                ReservationPriorityAction.REJECT,
                List.of()
        );
    }

    public static ReservationPriorityDecision displace(
            List<Long> reservationIds
    ) {
        return new ReservationPriorityDecision(
                ReservationPriorityAction
                        .DISPLACE_EXISTING_STANDBY,
                reservationIds
        );
    }
}