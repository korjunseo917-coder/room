package com.cnu.practiceroom.reservation.domain;

import java.time.ZonedDateTime;

public record ReservationRequestSnapshot(
        long requesterId,
        long roomId,
        ReservationType type,
        ZonedDateTime start,
        ZonedDateTime end
) {

    public ReservationRequestSnapshot {
        if (requesterId <= 0) {
            throw new IllegalArgumentException(
                    "신청자 ID는 0보다 커야 합니다."
            );
        }

        if (roomId <= 0) {
            throw new IllegalArgumentException(
                    "연습실 ID는 0보다 커야 합니다."
            );
        }

        if (type == null) {
            throw new IllegalArgumentException(
                    "예약 종류는 필수입니다."
            );
        }

        if (start == null || end == null) {
            throw new IllegalArgumentException(
                    "시작 및 종료 시각은 필수입니다."
            );
        }

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "종료 시각은 시작 시각보다 늦어야 합니다."
            );
        }
    }
}
