package com.cnu.practiceroom.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationPolicyTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private final ReservationPolicy policy =
            new ReservationPolicy();

    @Test
    @DisplayName("1시간 예약은 허용한다")
    void allowsOneHourReservation() {
        assertDoesNotThrow(
                () -> policy.validateTimeRange(
                        at(2026, 9, 15, 18, 0, 0),
                        at(2026, 9, 15, 19, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("자정을 지나는 3시간 예약은 허용한다")
    void allowsThreeHourReservationAcrossMidnight() {
        assertDoesNotThrow(
                () -> policy.validateTimeRange(
                        at(2026, 9, 30, 23, 0, 0),
                        at(2026, 10, 1, 2, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("자정을 지나는 4시간 예약은 거절한다")
    void rejectsFourHourReservationAcrossMidnight() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateTimeRange(
                        at(2026, 9, 30, 23, 0, 0),
                        at(2026, 10, 1, 3, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("30분에 시작하는 예약은 거절한다")
    void rejectsReservationStartingAtHalfPast() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateTimeRange(
                        at(2026, 9, 15, 18, 30, 0),
                        at(2026, 9, 15, 19, 30, 0)
                )
        );
    }

    @Test
    @DisplayName("시작과 종료 시각이 같으면 거절한다")
    void rejectsZeroDurationReservation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateTimeRange(
                        at(2026, 9, 15, 18, 0, 0),
                        at(2026, 9, 15, 18, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("종료 시각이 시작 시각보다 빠르면 거절한다")
    void rejectsNegativeDurationReservation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateTimeRange(
                        at(2026, 9, 15, 19, 0, 0),
                        at(2026, 9, 15, 18, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("오전 7시 전은 전날 운영일이다")
    void calculatesPreviousOperationalDateBeforeSeven() {
        LocalDate result =
                policy.calculateOperationalDate(
                        at(2026, 10, 1, 6, 0, 0)
                );

        assertEquals(
                LocalDate.of(2026, 9, 30),
                result
        );
    }

    @Test
    @DisplayName("오전 7시 정각부터 현재 운영일이다")
    void calculatesCurrentOperationalDateAtSeven() {
        LocalDate result =
                policy.calculateOperationalDate(
                        at(2026, 10, 1, 7, 0, 0)
                );

        assertEquals(
                LocalDate.of(2026, 10, 1),
                result
        );
    }

    @Test
    @DisplayName("1월 1일 오전 6시는 전년도 12월 운영월이다")
    void calculatesPreviousYearOperationalMonth() {
        YearMonth result =
                policy.calculateOperationalMonth(
                        at(2027, 1, 1, 6, 0, 0)
                );

        assertEquals(
                YearMonth.of(2026, 12),
                result
        );
    }

    @Test
    @DisplayName("9월 예약은 8월 27일 20시에 열린다")
    void calculatesSeptemberReservationOpenTime() {
        ZonedDateTime result =
                policy.calculateMonthlyOpenAt(
                        YearMonth.of(2026, 9)
                );

        assertEquals(
                at(2026, 8, 27, 20, 0, 0),
                result
        );
    }

    @Test
    @DisplayName("1월 예약은 전년도 12월 27일 20시에 열린다")
    void calculatesJanuaryReservationOpenTime() {
        ZonedDateTime result =
                policy.calculateMonthlyOpenAt(
                        YearMonth.of(2027, 1)
                );

        assertEquals(
                at(2026, 12, 27, 20, 0, 0),
                result
        );
    }

    @Test
    @DisplayName("운영월 경계를 넘는 예약은 두 달 중 늦은 개시 시각을 사용한다")
    void usesLatestOpenTimeAcrossOperationalMonths() {
        ZonedDateTime result =
                policy.calculateReservationOpenAt(
                        at(2026, 10, 1, 6, 0, 0),
                        at(2026, 10, 1, 9, 0, 0)
                );

        assertEquals(
                at(2026, 9, 26, 20, 0, 0),
                result
        );
    }

    @Test
    @DisplayName("일반 예약의 마감은 운영일 전날 22시다")
    void calculatesNormalApplicationDeadline() {
        ZonedDateTime result =
                policy.calculateApplicationDeadline(
                        at(2026, 9, 15, 18, 0, 0)
                );

        assertEquals(
                at(2026, 9, 14, 22, 0, 0),
                result
        );
    }

    @Test
    @DisplayName("자정 이후 예약은 전날 운영일을 기준으로 마감한다")
    void calculatesDeadlineForAfterMidnightReservation() {
        ZonedDateTime result =
                policy.calculateApplicationDeadline(
                        at(2026, 10, 1, 2, 0, 0)
                );

        assertEquals(
                at(2026, 9, 29, 22, 0, 0),
                result
        );
    }

    @Test
    @DisplayName("예약 개시 1초 전 신청은 거절한다")
    void rejectsApplicationOneSecondBeforeOpening() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateApplicationTime(
                        at(2026, 8, 27, 19, 59, 59),
                        at(2026, 9, 15, 18, 0, 0),
                        at(2026, 9, 15, 19, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("예약 개시 정각 신청은 허용한다")
    void allowsApplicationExactlyAtOpening() {
        assertDoesNotThrow(
                () -> policy.validateApplicationTime(
                        at(2026, 8, 27, 20, 0, 0),
                        at(2026, 9, 15, 18, 0, 0),
                        at(2026, 9, 15, 19, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("신청 마감 1초 전 신청은 허용한다")
    void allowsApplicationOneSecondBeforeDeadline() {
        assertDoesNotThrow(
                () -> policy.validateApplicationTime(
                        at(2026, 9, 14, 21, 59, 59),
                        at(2026, 9, 15, 18, 0, 0),
                        at(2026, 9, 15, 19, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("신청 마감 정각 신청은 거절한다")
    void rejectsApplicationExactlyAtDeadline() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateApplicationTime(
                        at(2026, 9, 14, 22, 0, 0),
                        at(2026, 9, 15, 18, 0, 0),
                        at(2026, 9, 15, 19, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("자정을 지나도 오전 7시 전이면 모두 이전 운영월에 포함한다")
    void countsAfterMidnightUsageInPreviousOperationalMonth() {
        Map<YearMonth, Long> result =
                policy.calculateUsageMinutesByOperationalMonth(
                        at(2026, 9, 30, 23, 0, 0),
                        at(2026, 10, 1, 2, 0, 0)
                );

        assertEquals(
                180L,
                result.get(YearMonth.of(2026, 9))
        );

        assertFalse(
                result.containsKey(YearMonth.of(2026, 10))
        );
    }

    @Test
    @DisplayName("오전 7시 운영월 경계를 넘으면 이용시간을 두 달로 나눈다")
    void splitsUsageAcrossOperationalMonths() {
        Map<YearMonth, Long> result =
                policy.calculateUsageMinutesByOperationalMonth(
                        at(2026, 10, 1, 6, 0, 0),
                        at(2026, 10, 1, 9, 0, 0)
                );

        assertEquals(
                60L,
                result.get(YearMonth.of(2026, 9))
        );

        assertEquals(
                120L,
                result.get(YearMonth.of(2026, 10))
        );
    }

    @Test
    @DisplayName("설정된 최대 이용시간을 사용한다")
    void usesConfiguredMaximumDuration() {
        ReservationPolicy customPolicy =
                new ReservationPolicy(
                        new ReservationPolicySettings(
                                60,
                                120,
                                LocalTime.of(7, 0),
                                5,
                                LocalTime.of(20, 0),
                                LocalTime.of(22, 0)
                        )
                );

        assertDoesNotThrow(
                () -> customPolicy.validateTimeRange(
                        at(2026, 9, 15, 18, 0, 0),
                        at(2026, 9, 15, 20, 0, 0)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> customPolicy.validateTimeRange(
                        at(2026, 9, 15, 18, 0, 0),
                        at(2026, 9, 15, 21, 0, 0)
                )
        );
    }

    @Test
    @DisplayName("설정된 예약 단위 경계를 사용한다")
    void usesConfiguredSlotBoundary() {
        ReservationPolicy customPolicy =
                new ReservationPolicy(
                        new ReservationPolicySettings(
                                30,
                                180,
                                LocalTime.of(7, 0),
                                5,
                                LocalTime.of(20, 0),
                                LocalTime.of(22, 0)
                        )
                );

        assertDoesNotThrow(
                () -> customPolicy.validateTimeRange(
                        at(2026, 9, 15, 18, 0, 0),
                        at(2026, 9, 15, 18, 30, 0)
                )
        );
    }

    @Test
    @DisplayName("설정된 운영일 시작 시각을 사용한다")
    void usesConfiguredOperationalDayStart() {
        ReservationPolicy customPolicy =
                new ReservationPolicy(
                        new ReservationPolicySettings(
                                60,
                                180,
                                LocalTime.of(6, 0),
                                5,
                                LocalTime.of(20, 0),
                                LocalTime.of(22, 0)
                        )
                );

        LocalDate result =
                customPolicy.calculateOperationalDate(
                        at(2026, 10, 1, 6, 0, 0)
                );

        assertEquals(
                LocalDate.of(2026, 10, 1),
                result
        );
    }

    @Test
    @DisplayName("최대 이용시간은 예약 단위로 나누어져야 한다")
    void rejectsMaximumDurationNotDivisibleBySlot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReservationPolicySettings(
                        60,
                        150,
                        LocalTime.of(7, 0),
                        5,
                        LocalTime.of(20, 0),
                        LocalTime.of(22, 0)
                )
        );
    }

    private ZonedDateTime at(
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int second
    ) {
        return ZonedDateTime.of(
                year,
                month,
                day,
                hour,
                minute,
                second,
                0,
                SEOUL
        );
    }
}