package com.cnu.practiceroom.reservation.application;

import java.time.ZonedDateTime;

public record DecideReservationCommand(
        long administratorId,
        long reservationId,
        AdminReservationDecision decision,
        String rejectionReason,
        ZonedDateTime decidedAt
) {

    public DecideReservationCommand {
        if (administratorId <= 0) {
            throw new IllegalArgumentException(
                    "관리자 ID는 0보다 커야 합니다."
            );
        }

        if (reservationId <= 0) {
            throw new IllegalArgumentException(
                    "예약 ID는 0보다 커야 합니다."
            );
        }

        if (decision == null) {
            throw new IllegalArgumentException(
                    "승인 또는 거절 결정은 필수입니다."
            );
        }

        if (decidedAt == null) {
            throw new IllegalArgumentException(
                    "처리 시각은 필수입니다."
            );
        }
    }
}
