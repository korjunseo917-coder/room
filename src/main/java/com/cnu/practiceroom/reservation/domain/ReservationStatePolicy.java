package com.cnu.practiceroom.reservation.domain;

import java.time.ZonedDateTime;

public final class ReservationStatePolicy {

    public ReservationStatus initialStatus() {
        return ReservationStatus.PENDING;
    }

    public ReservationStatus approve(
            ReservationStatus currentStatus,
            ZonedDateTime now,
            ZonedDateTime reservationStart
    ) {
        requireStatus(
                currentStatus,
                ReservationStatus.PENDING
        );

        requireBefore(
                now,
                reservationStart,
                "이용 시작 이후에는 승인할 수 없습니다."
        );

        return ReservationStatus.APPROVED;
    }

    public ReservationStatus reject(
            ReservationStatus currentStatus,
            ZonedDateTime now,
            ZonedDateTime reservationStart
    ) {
        requireStatus(
                currentStatus,
                ReservationStatus.PENDING
        );

        requireBefore(
                now,
                reservationStart,
                "이용 시작 이후에는 거절할 수 없습니다."
        );

        return ReservationStatus.REJECTED;
    }

    public ReservationStatus cancelByRequester(
            ReservationStatus currentStatus,
            ZonedDateTime now,
            ZonedDateTime reservationStart
    ) {
        requireStatus(
                currentStatus,
                ReservationStatus.PENDING,
                ReservationStatus.APPROVED,
                ReservationStatus.DISPLACED
        );

        requireBefore(
                now,
                reservationStart,
                "이용 시작 이후에는 신청자가 취소할 수 없습니다."
        );

        return ReservationStatus.CANCELED;
    }

    public ReservationStatus cancelOrVoidByAdmin(
            ReservationStatus currentStatus,
            ZonedDateTime now,
            ZonedDateTime reservationStart
    ) {
        requireTime(now, "현재 시각");
        requireTime(reservationStart, "예약 시작 시각");

        if (currentStatus == null) {
            throw new IllegalArgumentException(
                    "현재 상태는 필수입니다."
            );
        }

        if (currentStatus == ReservationStatus.COMPLETED) {
            return ReservationStatus.VOIDED;
        }

        requireStatus(
                currentStatus,
                ReservationStatus.PENDING,
                ReservationStatus.APPROVED
        );

        if (now.isBefore(reservationStart)) {
            return ReservationStatus.CANCELED;
        }

        if (currentStatus == ReservationStatus.APPROVED) {
            return ReservationStatus.VOIDED;
        }

        throw new IllegalStateException(
                "승인 대기 예약은 이용 시작 후 만료 처리해야 합니다."
        );
    }

    public ReservationStatus displaceStandby(
            ReservationType reservationType,
            ReservationStatus currentStatus
    ) {
        if (reservationType == null) {
            throw new IllegalArgumentException(
                    "예약 종류는 필수입니다."
            );
        }

        if (reservationType != ReservationType.STANDBY) {
            throw new IllegalStateException(
                    "정규예약은 대기예약처럼 밀려날 수 없습니다."
            );
        }

        requireStatus(
                currentStatus,
                ReservationStatus.PENDING,
                ReservationStatus.APPROVED
        );

        return ReservationStatus.DISPLACED;
    }

    public ReservationStatus restoreDisplaced(
            ReservationStatus currentStatus
    ) {
        requireStatus(
                currentStatus,
                ReservationStatus.DISPLACED
        );

        return ReservationStatus.PENDING;
    }

    public ReservationStatus expire(
            ReservationStatus currentStatus,
            ZonedDateTime now,
            ZonedDateTime reservationStart
    ) {
        requireStatus(
                currentStatus,
                ReservationStatus.PENDING
        );

        requireTime(now, "현재 시각");
        requireTime(reservationStart, "예약 시작 시각");

        if (now.isBefore(reservationStart)) {
            throw new IllegalStateException(
                    "이용 시작 전에는 만료 처리할 수 없습니다."
            );
        }

        return ReservationStatus.EXPIRED;
    }

    public ReservationStatus complete(
            ReservationStatus currentStatus,
            ZonedDateTime now,
            ZonedDateTime reservationEnd
    ) {
        requireStatus(
                currentStatus,
                ReservationStatus.APPROVED
        );

        requireTime(now, "현재 시각");
        requireTime(reservationEnd, "예약 종료 시각");

        if (now.isBefore(reservationEnd)) {
            throw new IllegalStateException(
                    "이용 종료 전에는 완료 처리할 수 없습니다."
            );
        }

        return ReservationStatus.COMPLETED;
    }

    private void requireBefore(
            ZonedDateTime now,
            ZonedDateTime boundary,
            String message
    ) {
        requireTime(now, "현재 시각");
        requireTime(boundary, "기준 시각");

        if (!now.isBefore(boundary)) {
            throw new IllegalStateException(message);
        }
    }

    private void requireTime(
            ZonedDateTime time,
            String name
    ) {
        if (time == null) {
            throw new IllegalArgumentException(
                    name + "은 필수입니다."
            );
        }
    }

    private void requireStatus(
            ReservationStatus currentStatus,
            ReservationStatus... allowedStatuses
    ) {
        if (currentStatus == null) {
            throw new IllegalArgumentException(
                    "현재 상태는 필수입니다."
            );
        }

        for (ReservationStatus allowedStatus
                : allowedStatuses) {

            if (currentStatus == allowedStatus) {
                return;
            }
        }

        throw new IllegalStateException(
                currentStatus
                        + " 상태에서는 해당 작업을 처리할 수 없습니다."
        );
    }
}