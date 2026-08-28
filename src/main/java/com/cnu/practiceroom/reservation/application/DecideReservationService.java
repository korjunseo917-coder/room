package com.cnu.practiceroom.reservation.application;

import com.cnu.practiceroom.reservation.domain.Reservation;
import com.cnu.practiceroom.reservation.domain.ReservationStatePolicy;
import com.cnu.practiceroom.reservation.domain.ReservationStatus;
import com.cnu.practiceroom.reservation.domain.ReservationType;
import com.cnu.practiceroom.reservation.repository.ReservationRepository;
import com.cnu.practiceroom.user.domain.User;
import com.cnu.practiceroom.user.domain.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DecideReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationStatePolicy statePolicy;
    private final DisplacedReservationRestorer restorer;
    private final ReservationOperationLocker operationLocker;

    public DecideReservationService(
            ReservationRepository reservationRepository,
            ReservationStatePolicy statePolicy,
            DisplacedReservationRestorer restorer,
            ReservationOperationLocker operationLocker
    ) {
        this.reservationRepository = reservationRepository;
        this.statePolicy = statePolicy;
        this.restorer = restorer;
        this.operationLocker = operationLocker;
    }

    @Transactional
    public DecideReservationResult decide(
            DecideReservationCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException(
                    "예약 처리 정보는 필수입니다."
            );
        }

        Reservation preview = reservationRepository
                .findById(command.reservationId())
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "예약을 찾을 수 없습니다."
                        )
                );

        List<Reservation> previewCandidates = List.of();

        if (command.decision()
                == AdminReservationDecision.REJECT
                && preview.getType()
                == ReservationType.REGULAR) {

            previewCandidates = reservationRepository
                    .findDisplacedByRegular(
                            preview.getId(),
                            ReservationStatus.DISPLACED
                    );
        }

        Set<Long> affectedUserIds = new LinkedHashSet<>();
        affectedUserIds.add(command.administratorId());
        affectedUserIds.add(preview.getRequester().getId());

        previewCandidates.forEach(
                reservation -> affectedUserIds.add(
                        reservation.getRequester().getId()
                )
        );

        List<User> lockedUsers = operationLocker
                .lockUsers(affectedUserIds);

        User administrator = lockedUsers.stream()
                .filter(
                        user -> user.getId()
                                .equals(command.administratorId())
                )
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "관리자를 찾을 수 없습니다."
                        )
                );

        if (administrator.getRole() != UserRole.ADMIN) {
            throw new IllegalStateException(
                    "관리자만 예약을 승인하거나 거절할 수 있습니다."
            );
        }

        operationLocker.lockRoom(
                preview.getRoom().getId()
        );

        Reservation reservation = reservationRepository
                .findByIdForUpdate(command.reservationId())
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "예약을 찾을 수 없습니다."
                        )
                );

        if (command.decision()
                == AdminReservationDecision.APPROVE) {

            reservation.approve(
                    administrator,
                    command.decidedAt(),
                    statePolicy
            );

        } else {
            reservation.reject(
                    administrator,
                    command.decidedAt(),
                    command.rejectionReason(),
                    statePolicy
            );
        }

        reservationRepository.flush();

        List<Long> restoredReservationIds = List.of();

        if (command.decision()
                == AdminReservationDecision.REJECT
                && reservation.getType()
                == ReservationType.REGULAR) {

            restoredReservationIds =
                    restorer.restoreDisplacedBy(
                            reservation,
                            command.decidedAt()
                    );
        }

        return new DecideReservationResult(
                reservation.getId(),
                reservation.getStatus(),
                restoredReservationIds
        );
    }
}
