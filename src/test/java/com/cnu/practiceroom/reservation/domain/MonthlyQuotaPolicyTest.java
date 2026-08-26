package com.cnu.practiceroom.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MonthlyQuotaPolicyTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private final MonthlyQuotaPolicy quotaPolicy =
            new MonthlyQuotaPolicy();

    private final ReservationPolicy reservationPolicy =
            new ReservationPolicy();

    @Test
    @DisplayName("월간 허용시간을 정확히 채우는 정규예약은 허용한다")
    void allowsReservationThatExactlyFillsQuota() {
        YearMonth september =
                YearMonth.of(2026, 9);

        Map<YearMonth, Long> quota =
                Map.of(september, 1_800L);

        Map<YearMonth, Long> used =
                Map.of(september, 1_620L);

        Map<YearMonth, Long> requested =
                Map.of(september, 180L);

        assertDoesNotThrow(
                () -> quotaPolicy.validateRegularReservation(
                        quota,
                        used,
                        requested
                )
        );
    }

    @Test
    @DisplayName("월간 허용시간을 1시간 초과하면 거절한다")
    void rejectsReservationExceedingQuotaByOneHour() {
        YearMonth september =
                YearMonth.of(2026, 9);

        Map<YearMonth, Long> quota =
                Map.of(september, 1_800L);

        Map<YearMonth, Long> used =
                Map.of(september, 1_680L);

        Map<YearMonth, Long> requested =
                Map.of(september, 180L);

        assertThrows(
                IllegalArgumentException.class,
                () -> quotaPolicy.validateRegularReservation(
                        quota,
                        used,
                        requested
                )
        );
    }

    @Test
    @DisplayName("기존 사용시간이 없으면 0분으로 계산한다")
    void treatsMissingUsageAsZero() {
        YearMonth september =
                YearMonth.of(2026, 9);

        Map<YearMonth, Long> quota =
                Map.of(september, 1_800L);

        Map<YearMonth, Long> used =
                Map.of();

        Map<YearMonth, Long> requested =
                Map.of(september, 180L);

        assertDoesNotThrow(
                () -> quotaPolicy.validateRegularReservation(
                        quota,
                        used,
                        requested
                )
        );
    }

    @Test
    @DisplayName("두 운영월의 남은 시간이 충분하면 경계 예약을 허용한다")
    void allowsReservationAcrossOperationalMonths() {
        Map<YearMonth, Long> requested =
                reservationPolicy
                        .calculateUsageMinutesByOperationalMonth(
                                at(2026, 10, 1, 6),
                                at(2026, 10, 1, 9)
                        );

        Map<YearMonth, Long> quota = Map.of(
                YearMonth.of(2026, 9), 1_800L,
                YearMonth.of(2026, 10), 1_500L
        );

        Map<YearMonth, Long> used = Map.of(
                YearMonth.of(2026, 9), 1_740L,
                YearMonth.of(2026, 10), 1_380L
        );

        assertDoesNotThrow(
                () -> quotaPolicy.validateRegularReservation(
                        quota,
                        used,
                        requested
                )
        );
    }

    @Test
    @DisplayName("경계 예약 중 한 운영월이라도 시간이 부족하면 전체를 거절한다")
    void rejectsEntireReservationWhenOneMonthExceedsQuota() {
        Map<YearMonth, Long> requested =
                reservationPolicy
                        .calculateUsageMinutesByOperationalMonth(
                                at(2026, 10, 1, 6),
                                at(2026, 10, 1, 9)
                        );

        Map<YearMonth, Long> quota = Map.of(
                YearMonth.of(2026, 9), 1_800L,
                YearMonth.of(2026, 10), 1_500L
        );

        Map<YearMonth, Long> used = Map.of(
                YearMonth.of(2026, 9), 1_740L,
                YearMonth.of(2026, 10), 1_440L
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> quotaPolicy.validateRegularReservation(
                        quota,
                        used,
                        requested
                )
        );
    }

    @Test
    @DisplayName("해당 운영월의 허용시간 설정이 없으면 거절한다")
    void rejectsReservationWhenQuotaIsMissing() {
        YearMonth september =
                YearMonth.of(2026, 9);

        assertThrows(
                IllegalArgumentException.class,
                () -> quotaPolicy.validateRegularReservation(
                        Map.of(),
                        Map.of(),
                        Map.of(september, 60L)
                )
        );
    }

    @Test
    @DisplayName("신청시간이 0분이면 거절한다")
    void rejectsZeroRequestedMinutes() {
        YearMonth september =
                YearMonth.of(2026, 9);

        assertThrows(
                IllegalArgumentException.class,
                () -> quotaPolicy.validateRegularReservation(
                        Map.of(september, 1_800L),
                        Map.of(),
                        Map.of(september, 0L)
                )
        );
    }

    @Test
    @DisplayName("정규예약 후 사용시간을 계산한다")
    void calculatesUsageAfterReservation() {
        YearMonth september =
                YearMonth.of(2026, 9);

        Map<YearMonth, Long> result =
                quotaPolicy.calculateUsageAfterReservation(
                        Map.of(september, 1_800L),
                        Map.of(september, 1_620L),
                        Map.of(september, 180L)
                );

        assertEquals(
                1_800L,
                result.get(september)
        );
    }

    @Test
    @DisplayName("월간 잔여 정규시간을 계산한다")
    void calculatesRemainingMinutes() {
        YearMonth september =
                YearMonth.of(2026, 9);

        long result =
                quotaPolicy.calculateRemainingMinutes(
                        september,
                        Map.of(september, 1_800L),
                        Map.of(september, 1_500L)
                );

        assertEquals(300L, result);
    }

    @Test
    @DisplayName("기존 사용시간이 허용시간을 넘은 상태에서는 신규예약을 거절한다")
    void rejectsReservationWhenUsageAlreadyExceedsQuota() {
        YearMonth september =
                YearMonth.of(2026, 9);

        assertThrows(
                IllegalArgumentException.class,
                () -> quotaPolicy.validateRegularReservation(
                        Map.of(september, 1_800L),
                        Map.of(september, 1_860L),
                        Map.of(september, 60L)
                )
        );
    }

    private ZonedDateTime at(
            int year,
            int month,
            int day,
            int hour
    ) {
        return ZonedDateTime.of(
                year,
                month,
                day,
                hour,
                0,
                0,
                0,
                SEOUL
        );
    }
}