package com.cnu.practiceroom.reservation.domain;

import java.time.ZonedDateTime;

public record ReservationSnapshot(
        long id,
        long requesterId,
        long roomId,
        ReservationType type,
        ReservationStatus status,
        ZonedDateTime start,
        ZonedDateTime end
) {

    public ReservationSnapshot {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "예약 ID는 0보다 커야 합니다."
            );
        }

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

        if (type == null || status == null) {
            throw new IllegalArgumentException(
                    "예약 종류와 상태는 필수입니다."
            );
        }

        if (start == null || end == null) {
            throw new IllegalArgumentException(
                    "시작 시각과 종료 시각은 필수입니다."
            );
        }

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "종료 시각은 시작 시각보다 늦어야 합니다."
            );
        }
    }

    public boolean isActive() {
        return status == ReservationStatus.PENDING
                || status == ReservationStatus.APPROVED;
    }
}