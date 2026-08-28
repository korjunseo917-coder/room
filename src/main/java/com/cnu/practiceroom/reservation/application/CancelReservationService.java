package com.cnu.practiceroom.reservation.application;

import com.cnu.practiceroom.reservation.domain.Reservation;
import com.cnu.practiceroom.reservation.domain.ReservationStatePolicy;
import com.cnu.practiceroom.reservation.domain.ReservationStatus;
import com.cnu.practiceroom.reservation.domain.ReservationType;
import com.cnu.practiceroom.reservation.repository.ReservationRepository;
import com.cnu.practiceroom.room.repository.RoomRepository;
import com.cnu.practiceroom.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CancelReservationService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationStatePolicy statePolicy;
    private final DisplacedReservationRestorer restorer;

    public CancelReservationService(
            UserRepository userRepository,
            RoomRepository roomRepository,
            ReservationRepository reservationRepository,
            ReservationStatePolicy statePolicy,
            DisplacedReservationRestorer restorer
    ) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.statePolicy = statePolicy;
        this.restorer = restorer;
    }

    @Transactional
    public CancelReservationResult cancel(
            CancelReservationCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException(
                    "예약 취소 정보는 필수입니다."
            );
        }

        Reservation preview = reservationRepository
                .findById(command.reservationId())
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "예약을 찾을 수 없습니다."
                        )
                );

        if (!preview.getRequester().getId()
                .equals(command.requesterId())) {

            throw new IllegalStateException(
                    "자신이 신청한 예약만 취소할 수 있습니다."
            );
        }

        List<Reservation> previewCandidates = List.of();

        if (preview.getType() == ReservationType.REGULAR) {
            previewCandidates = reservationRepository
                    .findDisplacedByRegular(
                            preview.getId(),
                            ReservationStatus.DISPLACED
                    );
        }

        lockAffectedUsers(
                command.requesterId(),
                previewCandidates
        );

        roomRepository.findByIdForUpdate(
                preview.getRoom().getId()
        ).orElseThrow(
                () -> new IllegalArgumentException(
                        "연습실을 찾을 수 없습니다."
                )
        );

        Reservation reservation = reservationRepository
                .findByIdForUpdate(command.reservationId())
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "예약을 찾을 수 없습니다."
                        )
                );

        if (!reservation.getRequester().getId()
                .equals(command.requesterId())) {

            throw new IllegalStateException(
                    "자신이 신청한 예약만 취소할 수 있습니다."
            );
        }

        reservation.cancelByRequester(
                command.canceledAt(),
                statePolicy
        );

        /*
         * 정규예약이 점유하던 시간대를 먼저 비운 뒤
         * 밀려났던 대기예약의 복구를 시도한다.
         */
        reservationRepository.flush();

        List<Long> restoredReservationIds = List.of();

        if (reservation.getType()
                == ReservationType.REGULAR) {

            restoredReservationIds =
                    restorer.restoreDisplacedBy(
                            reservation,
                            command.canceledAt()
                    );
        }

        return new CancelReservationResult(
                reservation.getId(),
                reservation.getStatus(),
                restoredReservationIds
        );
    }

    private void lockAffectedUsers(
            long requesterId,
            List<Reservation> displacedCandidates
    ) {
        Set<Long> userIds = new LinkedHashSet<>();
        userIds.add(requesterId);

        displacedCandidates.forEach(
                reservation -> userIds.add(
                        reservation.getRequester().getId()
                )
        );

        List<Long> sortedUserIds = new ArrayList<>(userIds);
        sortedUserIds.sort(Comparator.naturalOrder());

        List<?> lockedUsers = userRepository
                .findAllByIdForUpdate(sortedUserIds);

        if (lockedUsers.size() != sortedUserIds.size()) {
            throw new IllegalStateException(
                    "예약과 관련된 사용자를 찾을 수 없습니다."
            );
        }
    }
}
