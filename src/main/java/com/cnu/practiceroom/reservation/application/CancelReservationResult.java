package com.cnu.practiceroom.reservation.application;

import com.cnu.practiceroom.reservation.domain.ReservationStatus;

import java.util.List;

public record CancelReservationResult(
        long reservationId,
        ReservationStatus status,
        List<Long> restoredReservationIds
) {

    public CancelReservationResult {
        if (reservationId <= 0) {
            throw new IllegalArgumentException(
                    "예약 ID는 0보다 커야 합니다."
            );
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "예약 상태는 필수입니다."
            );
        }

        if (restoredReservationIds == null) {
            throw new IllegalArgumentException(
                    "복구된 예약 목록은 필수입니다."
            );
        }

        restoredReservationIds =
                List.copyOf(restoredReservationIds);
    }
}
