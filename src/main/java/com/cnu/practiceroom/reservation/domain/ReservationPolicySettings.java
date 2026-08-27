package com.cnu.practiceroom.reservation.domain;

import java.time.LocalTime;

public record ReservationPolicySettings(
        long slotMinutes,
        long maxDurationMinutes,
        LocalTime operationalDayStart,
        long monthlyOpenDaysBefore,
        LocalTime monthlyOpenTime,
        LocalTime applicationDeadlineTime
) {

    private static final long MINUTES_PER_DAY = 24 * 60;

    public ReservationPolicySettings {
        if (slotMinutes <= 0) {
            throw new IllegalArgumentException(
                    "예약 단위는 0분보다 커야 합니다."
            );
        }

        if (MINUTES_PER_DAY % slotMinutes != 0) {
            throw new IllegalArgumentException(
                    "예약 단위는 하루를 동일한 간격으로 나눌 수 있어야 합니다."
            );
        }

        if (maxDurationMinutes < slotMinutes) {
            throw new IllegalArgumentException(
                    "최대 이용시간은 예약 단위보다 짧을 수 없습니다."
            );
        }

        if (maxDurationMinutes % slotMinutes != 0) {
            throw new IllegalArgumentException(
                    "최대 이용시간은 예약 단위로 나누어져야 합니다."
            );
        }

        if (operationalDayStart == null) {
            throw new IllegalArgumentException(
                    "운영일 시작 시각은 필수입니다."
            );
        }

        requireMinutePrecision(
                operationalDayStart,
                "운영일 시작 시각"
        );

        long operationalStartMinute =
                operationalDayStart.getHour() * 60L
                        + operationalDayStart.getMinute();

        if (operationalStartMinute % slotMinutes != 0) {
            throw new IllegalArgumentException(
                    "운영일 시작 시각은 예약 단위 경계와 일치해야 합니다."
            );
        }

        if (monthlyOpenDaysBefore < 0) {
            throw new IllegalArgumentException(
                    "월별 예약 오픈 기준일은 음수일 수 없습니다."
            );
        }

        if (monthlyOpenTime == null) {
            throw new IllegalArgumentException(
                    "월별 예약 오픈 시각은 필수입니다."
            );
        }

        requireMinutePrecision(
                monthlyOpenTime,
                "월별 예약 오픈 시각"
        );

        if (applicationDeadlineTime == null) {
            throw new IllegalArgumentException(
                    "예약 신청 마감 시각은 필수입니다."
            );
        }

        requireMinutePrecision(
                applicationDeadlineTime,
                "예약 신청 마감 시각"
        );
    }

    public static ReservationPolicySettings standard() {
        return new ReservationPolicySettings(
                60,
                180,
                LocalTime.of(7, 0),
                5,
                LocalTime.of(20, 0),
                LocalTime.of(22, 0)
        );
    }

    private static void requireMinutePrecision(
            LocalTime time,
            String name
    ) {
        if (time.getSecond() != 0 || time.getNano() != 0) {
            throw new IllegalArgumentException(
                    name + "은 분 단위로 설정해야 합니다."
            );
        }
    }
}
