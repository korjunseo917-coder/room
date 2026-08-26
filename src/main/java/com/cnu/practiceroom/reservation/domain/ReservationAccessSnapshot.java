package com.cnu.practiceroom.reservation.domain;

public record ReservationAccessSnapshot(
        long reservationId,
        long requesterId,
        long clubId
) {

    public ReservationAccessSnapshot {
        if (reservationId <= 0) {
            throw new IllegalArgumentException(
                    "예약 ID는 0보다 커야 합니다."
            );
        }

        if (requesterId <= 0) {
            throw new IllegalArgumentException(
                    "신청자 ID는 0보다 커야 합니다."
            );
        }

        if (clubId <= 0) {
            throw new IllegalArgumentException(
                    "동아리 ID는 0보다 커야 합니다."
            );
        }
    }
}