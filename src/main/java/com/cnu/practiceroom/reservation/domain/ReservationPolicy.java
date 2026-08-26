package com.cnu.practiceroom.reservation.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReservationPolicy {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private static final long SLOT_MINUTES = 60;
    private static final long MAX_DURATION_MINUTES = 180;

    private static final LocalTime OPERATIONAL_DAY_START =
            LocalTime.of(7, 0);

    private static final long MONTHLY_OPEN_DAYS_BEFORE = 5;

    private static final LocalTime MONTHLY_OPEN_TIME =
            LocalTime.of(20, 0);

    private static final LocalTime APPLICATION_DEADLINE_TIME =
            LocalTime.of(22, 0);

    public void validateTimeRange(
            ZonedDateTime start,
            ZonedDateTime end
    ) {
        if (start == null || end == null) {
            throw new IllegalArgumentException(
                    "시작 시각과 종료 시각은 필수입니다."
            );
        }

        ZonedDateTime seoulStart =
                start.withZoneSameInstant(SEOUL);

        ZonedDateTime seoulEnd =
                end.withZoneSameInstant(SEOUL);

        if (!seoulEnd.isAfter(seoulStart)) {
            throw new IllegalArgumentException(
                    "종료 시각은 시작 시각보다 늦어야 합니다."
            );
        }

        if (!isOnTheHour(seoulStart)
                || !isOnTheHour(seoulEnd)) {
            throw new IllegalArgumentException(
                    "예약은 정각을 기준으로 신청해야 합니다."
            );
        }

        long durationMinutes =
                Duration.between(seoulStart, seoulEnd)
                        .toMinutes();

        if (durationMinutes % SLOT_MINUTES != 0) {
            throw new IllegalArgumentException(
                    "예약은 1시간 단위여야 합니다."
            );
        }

        if (durationMinutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException(
                    "예약은 최대 3시간까지 가능합니다."
            );
        }
    }

    public LocalDate calculateOperationalDate(
            ZonedDateTime dateTime
    ) {
        if (dateTime == null) {
            throw new IllegalArgumentException(
                    "날짜와 시각은 필수입니다."
            );
        }

        ZonedDateTime seoulDateTime =
                dateTime.withZoneSameInstant(SEOUL);

        LocalDate calendarDate =
                seoulDateTime.toLocalDate();

        LocalTime calendarTime =
                seoulDateTime.toLocalTime();

        if (calendarTime.isBefore(OPERATIONAL_DAY_START)) {
            return calendarDate.minusDays(1);
        }

        return calendarDate;
    }

    public YearMonth calculateOperationalMonth(
            ZonedDateTime dateTime
    ) {
        LocalDate operationalDate =
                calculateOperationalDate(dateTime);

        return YearMonth.from(operationalDate);
    }

    public ZonedDateTime calculateMonthlyOpenAt(
            YearMonth operationalMonth
    ) {
        if (operationalMonth == null) {
            throw new IllegalArgumentException(
                    "운영월은 필수입니다."
            );
        }

        LocalDate openDate = operationalMonth
                .atDay(1)
                .minusDays(MONTHLY_OPEN_DAYS_BEFORE);

        return openDate
                .atTime(MONTHLY_OPEN_TIME)
                .atZone(SEOUL);
    }

    public ZonedDateTime calculateReservationOpenAt(
            ZonedDateTime start,
            ZonedDateTime end
    ) {
        validateTimeRange(start, end);

        ZonedDateTime cursor =
                start.withZoneSameInstant(SEOUL);

        ZonedDateTime seoulEnd =
                end.withZoneSameInstant(SEOUL);

        ZonedDateTime latestOpenAt = null;

        while (cursor.isBefore(seoulEnd)) {
            YearMonth operationalMonth =
                    calculateOperationalMonth(cursor);

            ZonedDateTime monthlyOpenAt =
                    calculateMonthlyOpenAt(operationalMonth);

            if (latestOpenAt == null
                    || monthlyOpenAt.isAfter(latestOpenAt)) {
                latestOpenAt = monthlyOpenAt;
            }

            cursor = cursor.plusMinutes(SLOT_MINUTES);
        }

        return latestOpenAt;
    }

    public ZonedDateTime calculateApplicationDeadline(
            ZonedDateTime start
    ) {
        LocalDate operationalDate =
                calculateOperationalDate(start);

        return operationalDate
                .minusDays(1)
                .atTime(APPLICATION_DEADLINE_TIME)
                .atZone(SEOUL);
    }

    public void validateApplicationTime(
            ZonedDateTime requestedAt,
            ZonedDateTime start,
            ZonedDateTime end
    ) {
        if (requestedAt == null) {
            throw new IllegalArgumentException(
                    "신청 시각은 필수입니다."
            );
        }

        validateTimeRange(start, end);

        ZonedDateTime seoulRequestedAt =
                requestedAt.withZoneSameInstant(SEOUL);

        ZonedDateTime openAt =
                calculateReservationOpenAt(start, end);

        ZonedDateTime deadline =
                calculateApplicationDeadline(start);

        if (seoulRequestedAt.isBefore(openAt)) {
            throw new IllegalArgumentException(
                    "아직 예약 신청 기간이 아닙니다."
            );
        }

        if (!seoulRequestedAt.isBefore(deadline)) {
            throw new IllegalArgumentException(
                    "예약 신청 기간이 마감되었습니다."
            );
        }
    }

    public Map<YearMonth, Long>
    calculateUsageMinutesByOperationalMonth(
            ZonedDateTime start,
            ZonedDateTime end
    ) {
        validateTimeRange(start, end);

        ZonedDateTime cursor =
                start.withZoneSameInstant(SEOUL);

        ZonedDateTime seoulEnd =
                end.withZoneSameInstant(SEOUL);

        Map<YearMonth, Long> usageMinutes =
                new LinkedHashMap<>();

        while (cursor.isBefore(seoulEnd)) {
            YearMonth operationalMonth =
                    calculateOperationalMonth(cursor);

            usageMinutes.merge(
                    operationalMonth,
                    SLOT_MINUTES,
                    Long::sum
            );

            cursor = cursor.plusMinutes(SLOT_MINUTES);
        }

        return Map.copyOf(usageMinutes);
    }

    private boolean isOnTheHour(
            ZonedDateTime dateTime
    ) {
        return dateTime.getMinute() == 0
                && dateTime.getSecond() == 0
                && dateTime.getNano() == 0;
    }
}