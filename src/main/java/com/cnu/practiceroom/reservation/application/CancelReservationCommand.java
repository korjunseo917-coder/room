package com.cnu.practiceroom.reservation.application;

import java.time.ZonedDateTime;

public record CancelReservationCommand(
        long requesterId,
        long reservationId,
        ZonedDateTime canceledAt
) {

    public CancelReservationCommand {
        if (requesterId <= 0) {
            throw new IllegalArgumentException(
                    "취소 요청자 ID는 0보다 커야 합니다."
            );
        }

        if (reservationId <= 0) {
            throw new IllegalArgumentException(
                    "예약 ID는 0보다 커야 합니다."
            );
        }

        if (canceledAt == null) {
            throw new IllegalArgumentException(
                    "취소 시각은 필수입니다."
            );
        }
    }
}
