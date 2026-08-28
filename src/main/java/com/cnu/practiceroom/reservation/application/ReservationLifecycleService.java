package com.cnu.practiceroom.reservation.application;

import com.cnu.practiceroom.reservation.domain.Reservation;
import com.cnu.practiceroom.reservation.domain.ReservationStatePolicy;
import com.cnu.practiceroom.reservation.domain.ReservationStatus;
import com.cnu.practiceroom.reservation.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class ReservationLifecycleService {

    private final ReservationRepository reservationRepository;
    private final ReservationStatePolicy statePolicy;

    public ReservationLifecycleService(
            ReservationRepository reservationRepository,
            ReservationStatePolicy statePolicy
    ) {
        this.reservationRepository = reservationRepository;
        this.statePolicy = statePolicy;
    }

    @Transactional
    public ReservationLifecycleResult process(
            ZonedDateTime now
    ) {
        if (now == null) {
            throw new IllegalArgumentException(
                    "현재 시각은 필수입니다."
            );
        }

        List<Reservation> pendingToExpire =
                reservationRepository
                        .findPendingToExpireForUpdate(
                                ReservationStatus.PENDING,
                                now.toInstant()
                        );

        pendingToExpire.forEach(
                reservation -> reservation.expire(
                        now,
                        statePolicy
                )
        );

        List<Reservation> approvedToComplete =
                reservationRepository
                        .findApprovedToCompleteForUpdate(
                                ReservationStatus.APPROVED,
                                now.toInstant()
                        );

        approvedToComplete.forEach(
                reservation -> reservation.complete(
                        now,
                        statePolicy
                )
        );

        reservationRepository.flush();

        return new ReservationLifecycleResult(
                pendingToExpire.stream()
                        .map(Reservation::getId)
                        .toList(),
                approvedToComplete.stream()
                        .map(Reservation::getId)
                        .toList()
        );
    }
}
