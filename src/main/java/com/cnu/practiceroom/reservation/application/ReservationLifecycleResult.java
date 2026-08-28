package com.cnu.practiceroom.reservation.application;

import java.util.List;

public record ReservationLifecycleResult(
        List<Long> expiredReservationIds,
        List<Long> completedReservationIds
) {

    public ReservationLifecycleResult {
        if (expiredReservationIds == null
                || completedReservationIds == null) {
            throw new IllegalArgumentException(
                    "상태 전환 결과 목록은 필수입니다."
            );
        }

        expiredReservationIds =
                List.copyOf(expiredReservationIds);

        completedReservationIds =
                List.copyOf(completedReservationIds);
    }
}
