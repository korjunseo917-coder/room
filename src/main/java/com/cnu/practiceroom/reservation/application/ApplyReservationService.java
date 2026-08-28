package com.cnu.practiceroom.reservation.application;

import com.cnu.practiceroom.reservation.domain.ClubMonthlyQuota;
import com.cnu.practiceroom.reservation.domain.MonthlyQuotaPolicy;
import com.cnu.practiceroom.reservation.domain.Reservation;
import com.cnu.practiceroom.reservation.domain.ReservationPolicy;
import com.cnu.practiceroom.reservation.domain.ReservationPriorityAction;
import com.cnu.practiceroom.reservation.domain.ReservationPriorityDecision;
import com.cnu.practiceroom.reservation.domain.ReservationPriorityPolicy;
import com.cnu.practiceroom.reservation.domain.ReservationRequestSnapshot;
import com.cnu.practiceroom.reservation.domain.ReservationStatePolicy;
import com.cnu.practiceroom.reservation.domain.ReservationStatus;
import com.cnu.practiceroom.reservation.domain.ReservationType;
import com.cnu.practiceroom.reservation.repository.ClubMonthlyQuotaRepository;
import com.cnu.practiceroom.reservation.repository.ReservationRepository;
import com.cnu.practiceroom.room.domain.Room;
import com.cnu.practiceroom.room.repository.RoomRepository;
import com.cnu.practiceroom.user.domain.User;
import com.cnu.practiceroom.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApplyReservationService {

    private static final EnumSet<ReservationStatus>
            ACTIVE_STATUSES = EnumSet.of(
                    ReservationStatus.PENDING,
                    ReservationStatus.APPROVED
            );

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final ClubMonthlyQuotaRepository quotaRepository;
    private final ReservationPolicy reservationPolicy;
    private final MonthlyQuotaPolicy monthlyQuotaPolicy;
    private final ReservationPriorityPolicy priorityPolicy;
    private final ReservationStatePolicy statePolicy;

    public ApplyReservationService(
            UserRepository userRepository,
            RoomRepository roomRepository,
            ReservationRepository reservationRepository,
            ClubMonthlyQuotaRepository quotaRepository,
            ReservationPolicy reservationPolicy,
            MonthlyQuotaPolicy monthlyQuotaPolicy,
            ReservationPriorityPolicy priorityPolicy,
            ReservationStatePolicy statePolicy
    ) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.quotaRepository = quotaRepository;
        this.reservationPolicy = reservationPolicy;
        this.monthlyQuotaPolicy = monthlyQuotaPolicy;
        this.priorityPolicy = priorityPolicy;
        this.statePolicy = statePolicy;
    }

    @Transactional
    public ApplyReservationResult apply(
            ApplyReservationCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException(
                    "예약 신청 정보는 필수입니다."
            );
        }

        /*
         * 모든 신청이 동일한 순서로 행을 잠근다.
         * 사용자 → 연습실 → 운영월별 쿼터 순서다.
         */
        User requester = userRepository
                .findByIdForUpdate(command.requesterId())
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "신청자를 찾을 수 없습니다."
                        )
                );

        Room room = roomRepository
                .findByIdForUpdate(command.roomId())
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "연습실을 찾을 수 없습니다."
                        )
                );

        Reservation newReservation = Reservation.pending(
                requester,
                room,
                command.type(),
                command.start(),
                command.end(),
                command.requestedAt(),
                reservationPolicy
        );

        if (!reservationRepository
                .findRequesterOverlappingOtherRooms(
                        requester.getId(),
                        room.getId(),
                        ACTIVE_STATUSES,
                        newReservation.getStartAt(),
                        newReservation.getEndAt()
                )
                .isEmpty()) {

            throw new IllegalStateException(
                    "같은 사용자는 다른 연습실을 동일 시간에 예약할 수 없습니다."
            );
        }

        if (command.type() == ReservationType.REGULAR) {
            validateMonthlyQuota(newReservation);
        }

        List<Reservation> overlappingReservations =
                reservationRepository.findOverlapping(
                        room.getId(),
                        ACTIVE_STATUSES,
                        newReservation.getStartAt(),
                        newReservation.getEndAt()
                );

        ReservationPriorityDecision decision =
                priorityPolicy.decideNewRequest(
                        new ReservationRequestSnapshot(
                                requester.getId(),
                                room.getId(),
                                command.type(),
                                command.start(),
                                command.end()
                        ),
                        overlappingReservations.stream()
                                .map(Reservation::toSnapshot)
                                .toList()
                );

        if (decision.action()
                == ReservationPriorityAction.REJECT) {

            throw new IllegalStateException(
                    "해당 연습실 시간대에 이미 활성 예약이 있습니다."
            );
        }

        List<Reservation> displacedReservations = List.of();

        if (decision.action()
                == ReservationPriorityAction
                .DISPLACE_EXISTING_STANDBY) {

            displacedReservations = overlappingReservations
                    .stream()
                    .filter(
                            reservation ->
                                    decision.displacedReservationIds()
                                            .contains(reservation.getId())
                    )
                    .toList();

            displacedReservations.forEach(
                    reservation -> reservation.displace(
                            command.requestedAt(),
                            statePolicy
                    )
            );

            /*
             * 기존 대기예약을 먼저 비활성 상태로 DB에 반영해야
             * 같은 방 시간 중복 제약조건을 통과할 수 있다.
             */
            reservationRepository.flush();
        }

        Reservation savedReservation =
                reservationRepository.saveAndFlush(
                        newReservation
                );

        displacedReservations.forEach(
                reservation -> reservation.recordDisplacedBy(
                        savedReservation
                )
        );

        reservationRepository.flush();

        return new ApplyReservationResult(
                savedReservation.getId(),
                savedReservation.getStatus(),
                displacedReservations.stream()
                        .map(Reservation::getId)
                        .toList()
        );
    }

    private void validateMonthlyQuota(
            Reservation reservation
    ) {
        Map<YearMonth, Long> requestedMinutes =
                reservation.getUsageMinutesByMonth();

        List<YearMonth> operationalMonths =
                requestedMinutes.keySet()
                        .stream()
                        .sorted()
                        .toList();

        Map<YearMonth, Long> quotaMinutes =
                new LinkedHashMap<>();

        Map<YearMonth, Long> usedMinutes =
                new LinkedHashMap<>();

        for (YearMonth operationalMonth
                : operationalMonths) {

            ClubMonthlyQuota quota = quotaRepository
                    .findForUpdate(
                            reservation.getClub().getId(),
                            operationalMonth.atDay(1)
                    )
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    operationalMonth
                                            + "의 월간 허용시간이 설정되지 않았습니다."
                            )
                    );

            quotaMinutes.put(
                    operationalMonth,
                    quota.getQuotaMinutes()
            );

            usedMinutes.put(
                    operationalMonth,
                    reservationRepository
                            .sumCountedRegularUsageMinutes(
                                    reservation.getClub().getId(),
                                    operationalMonth.atDay(1)
                            )
            );
        }

        monthlyQuotaPolicy.validateRegularReservation(
                quotaMinutes,
                usedMinutes,
                requestedMinutes
        );
    }
}
