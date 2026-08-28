package com.cnu.practiceroom.reservation.application;

import com.cnu.practiceroom.reservation.domain.Reservation;
import com.cnu.practiceroom.reservation.domain.ReservationPriorityPolicy;
import com.cnu.practiceroom.reservation.domain.ReservationSnapshot;
import com.cnu.practiceroom.reservation.domain.ReservationStatePolicy;
import com.cnu.practiceroom.reservation.domain.ReservationStatus;
import com.cnu.practiceroom.reservation.repository.ReservationRepository;
import com.cnu.practiceroom.user.domain.UserRole;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DisplacedReservationRestorer {

    private static final EnumSet<ReservationStatus>
            ACTIVE_STATUSES = EnumSet.of(
                    ReservationStatus.PENDING,
                    ReservationStatus.APPROVED
            );

    private final ReservationRepository reservationRepository;
    private final ReservationPriorityPolicy priorityPolicy;
    private final ReservationStatePolicy statePolicy;

    public DisplacedReservationRestorer(
            ReservationRepository reservationRepository,
            ReservationPriorityPolicy priorityPolicy,
            ReservationStatePolicy statePolicy
    ) {
        this.reservationRepository = reservationRepository;
        this.priorityPolicy = priorityPolicy;
        this.statePolicy = statePolicy;
    }

    public List<Long> restoreDisplacedBy(
            Reservation inactiveRegularReservation,
            ZonedDateTime now
    ) {
        if (inactiveRegularReservation == null
                || inactiveRegularReservation.getId() == null) {
            throw new IllegalArgumentException(
                    "비활성화된 정규예약은 필수입니다."
            );
        }

        List<Reservation> candidates = reservationRepository
                .findDisplacedByRegularForUpdate(
                        inactiveRegularReservation.getId(),
                        ReservationStatus.DISPLACED
                );

        List<Long> restoredReservationIds =
                new ArrayList<>();

        for (Reservation candidate : candidates) {
            List<ReservationSnapshot> activeReservations =
                    findActiveConflicts(candidate);

            boolean requesterIsActiveMember =
                    candidate.getRequester().isActive()
                            && candidate.getRequester().getRole()
                            == UserRole.MEMBER
                            && candidate.getRequester().getClub()
                            != null;

            boolean canRestore = priorityPolicy.canRestore(
                    candidate.toSnapshot(),
                    activeReservations,
                    requesterIsActiveMember,
                    now
            );

            if (!canRestore) {
                continue;
            }

            candidate.restoreDisplaced(statePolicy);

            /*
             * 최초 신청 시각이 앞선 예약부터 실제 DB에 반영한다.
             * 이후 후보는 방금 복구된 예약까지 포함해 다시 검사한다.
             */
            reservationRepository.flush();
            restoredReservationIds.add(candidate.getId());
        }

        return List.copyOf(restoredReservationIds);
    }

    private List<ReservationSnapshot> findActiveConflicts(
            Reservation candidate
    ) {
        List<Reservation> sameRoom =
                reservationRepository.findOverlapping(
                        candidate.getRoom().getId(),
                        ACTIVE_STATUSES,
                        candidate.getStartAt(),
                        candidate.getEndAt()
                );

        List<Reservation> sameRequesterOtherRooms =
                reservationRepository
                        .findRequesterOverlappingOtherRooms(
                                candidate.getRequester().getId(),
                                candidate.getRoom().getId(),
                                ACTIVE_STATUSES,
                                candidate.getStartAt(),
                                candidate.getEndAt()
                        );

        Map<Long, ReservationSnapshot> unique =
                new LinkedHashMap<>();

        sameRoom.forEach(
                reservation -> unique.put(
                        reservation.getId(),
                        reservation.toSnapshot()
                )
        );

        sameRequesterOtherRooms.forEach(
                reservation -> unique.put(
                        reservation.getId(),
                        reservation.toSnapshot()
                )
        );

        return List.copyOf(unique.values());
    }
}
