package com.cnu.practiceroom.reservation.domain;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MonthlyQuotaPolicy {

    private static final long MINUTES_PER_HOUR = 60;

    public void validateRegularReservation(
            Map<YearMonth, Long> quotaMinutesByMonth,
            Map<YearMonth, Long> usedMinutesByMonth,
            Map<YearMonth, Long> requestedMinutesByMonth
    ) {
        validateMinuteMap(
                "월간 허용시간",
                quotaMinutesByMonth,
                true
        );

        validateMinuteMap(
                "기존 사용시간",
                usedMinutesByMonth,
                true
        );

        validateMinuteMap(
                "신청시간",
                requestedMinutesByMonth,
                false
        );

        if (requestedMinutesByMonth.isEmpty()) {
            throw new IllegalArgumentException(
                    "신청시간은 비어 있을 수 없습니다."
            );
        }

        for (Map.Entry<YearMonth, Long> entry
                : requestedMinutesByMonth.entrySet()) {

            YearMonth operationalMonth =
                    entry.getKey();

            long requestedMinutes =
                    entry.getValue();

            Long quotaValue =
                    quotaMinutesByMonth.get(operationalMonth);

            if (quotaValue == null) {
                throw new IllegalArgumentException(
                        operationalMonth
                                + "의 월간 허용시간이 설정되지 않았습니다."
                );
            }

            long quotaMinutes = quotaValue;

            long usedMinutes =
                    usedMinutesByMonth.getOrDefault(
                            operationalMonth,
                            0L
                    );

            if (usedMinutes > quotaMinutes) {
                throw new IllegalArgumentException(
                        operationalMonth
                                + "의 기존 사용시간이 허용시간을 초과했습니다."
                );
            }

            long remainingMinutes =
                    quotaMinutes - usedMinutes;

            if (requestedMinutes > remainingMinutes) {
                throw new IllegalArgumentException(
                        operationalMonth
                                + "의 월간 정규시간이 부족합니다."
                );
            }
        }
    }

    public Map<YearMonth, Long>
    calculateUsageAfterReservation(
            Map<YearMonth, Long> quotaMinutesByMonth,
            Map<YearMonth, Long> usedMinutesByMonth,
            Map<YearMonth, Long> requestedMinutesByMonth
    ) {
        validateRegularReservation(
                quotaMinutesByMonth,
                usedMinutesByMonth,
                requestedMinutesByMonth
        );

        Map<YearMonth, Long> result =
                new LinkedHashMap<>(usedMinutesByMonth);

        for (Map.Entry<YearMonth, Long> entry
                : requestedMinutesByMonth.entrySet()) {

            result.merge(
                    entry.getKey(),
                    entry.getValue(),
                    Long::sum
            );
        }

        return Map.copyOf(result);
    }

    public long calculateRemainingMinutes(
            YearMonth operationalMonth,
            Map<YearMonth, Long> quotaMinutesByMonth,
            Map<YearMonth, Long> usedMinutesByMonth
    ) {
        if (operationalMonth == null) {
            throw new IllegalArgumentException(
                    "운영월은 필수입니다."
            );
        }

        validateMinuteMap(
                "월간 허용시간",
                quotaMinutesByMonth,
                true
        );

        validateMinuteMap(
                "기존 사용시간",
                usedMinutesByMonth,
                true
        );

        Long quotaValue =
                quotaMinutesByMonth.get(operationalMonth);

        if (quotaValue == null) {
            throw new IllegalArgumentException(
                    operationalMonth
                            + "의 월간 허용시간이 설정되지 않았습니다."
            );
        }

        long usedMinutes =
                usedMinutesByMonth.getOrDefault(
                        operationalMonth,
                        0L
                );

        return Math.max(
                0L,
                quotaValue - usedMinutes
        );
    }

    private void validateMinuteMap(
            String name,
            Map<YearMonth, Long> minuteMap,
            boolean allowZero
    ) {
        if (minuteMap == null) {
            throw new IllegalArgumentException(
                    name + "은 필수입니다."
            );
        }

        for (Map.Entry<YearMonth, Long> entry
                : minuteMap.entrySet()) {

            YearMonth operationalMonth =
                    entry.getKey();

            Long minutes =
                    entry.getValue();

            if (operationalMonth == null) {
                throw new IllegalArgumentException(
                        name + "의 운영월은 필수입니다."
                );
            }

            if (minutes == null) {
                throw new IllegalArgumentException(
                        name + "은 필수입니다."
                );
            }

            if (allowZero && minutes < 0) {
                throw new IllegalArgumentException(
                        name + "은 음수일 수 없습니다."
                );
            }

            if (!allowZero && minutes <= 0) {
                throw new IllegalArgumentException(
                        name + "은 0보다 커야 합니다."
                );
            }

            if (minutes % MINUTES_PER_HOUR != 0) {
                throw new IllegalArgumentException(
                        name + "은 1시간 단위여야 합니다."
                );
            }
        }
    }
}
