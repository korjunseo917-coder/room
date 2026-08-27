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

    private final ReservationPolicySettings settings;

    public ReservationPolicy() {
        this(ReservationPolicySettings.standard());
    }

    public ReservationPolicy(
            ReservationPolicySettings settings
    ) {
        if (settings == null) {
            throw new IllegalArgumentException(
                    "예약 정책 설정은 필수입니다."
            );
        }

        this.settings = settings;
    }

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

        if (!isOnSlotBoundary(seoulStart)
                || !isOnSlotBoundary(seoulEnd)) {
            throw new IllegalArgumentException(
                    "시작과 종료 시각은 예약 단위 경계와 일치해야 합니다."
            );
        }

        long durationMinutes =
                Duration.between(seoulStart, seoulEnd)
                        .toMinutes();

        if (durationMinutes % settings.slotMinutes() != 0) {
            throw new IllegalArgumentException(
                    "예약은 "
                            + settings.slotMinutes()
                            + "분 단위여야 합니다."
            );
        }

        if (durationMinutes
                > settings.maxDurationMinutes()) {
            throw new IllegalArgumentException(
                    "예약은 최대 "
                            + settings.maxDurationMinutes()
                            + "분까지 가능합니다."
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

        if (calendarTime.isBefore(
                settings.operationalDayStart()
        )) {
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
                .minusDays(
                        settings.monthlyOpenDaysBefore()
                );

        return openDate
                .atTime(settings.monthlyOpenTime())
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
                    calculateMonthlyOpenAt(
                            operationalMonth
                    );

            if (latestOpenAt == null
                    || monthlyOpenAt.isAfter(latestOpenAt)) {
                latestOpenAt = monthlyOpenAt;
            }

            cursor = cursor.plusMinutes(
                    settings.slotMinutes()
            );
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
                .atTime(
                        settings.applicationDeadlineTime()
                )
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
                    settings.slotMinutes(),
                    Long::sum
            );

            cursor = cursor.plusMinutes(
                    settings.slotMinutes()
            );
        }

        return Map.copyOf(usageMinutes);
    }

    private boolean isOnSlotBoundary(
            ZonedDateTime dateTime
    ) {
        if (dateTime.getSecond() != 0
                || dateTime.getNano() != 0) {
            return false;
        }

        long minuteOfDay =
                dateTime.getHour() * 60L
                        + dateTime.getMinute();

        return minuteOfDay
                % settings.slotMinutes() == 0;
    }
}