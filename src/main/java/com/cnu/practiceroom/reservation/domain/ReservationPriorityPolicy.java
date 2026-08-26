package com.cnu.practiceroom.reservation.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ReservationPriorityPolicy {

    public ReservationPriorityDecision decideNewRequest(
            ReservationSnapshot newReservation,
            List<ReservationSnapshot> existingReservations
    ) {
        if (newReservation == null) {
            throw new IllegalArgumentException(
                    "신규 예약은 필수입니다."
            );
        }

        if (existingReservations == null) {
            throw new IllegalArgumentException(
                    "기존 예약 목록은 필수입니다."
            );
        }

        if (newReservation.status()
                != ReservationStatus.PENDING) {

            throw new IllegalArgumentException(
                    "신규 예약은 승인 대기 상태여야 합니다."
            );
        }

        List<ReservationSnapshot> conflicts =
                findRoomConflicts(
                        newReservation,
                        existingReservations
                );

        if (conflicts.isEmpty()) {
            return ReservationPriorityDecision.allow();
        }

        if (newReservation.type()
                == ReservationType.STANDBY) {

            return ReservationPriorityDecision.reject();
        }

        boolean hasRegularConflict =
                conflicts.stream()
                        .anyMatch(
                                reservation ->
                                        reservation.type()
                                                == ReservationType.REGULAR
                        );

        if (hasRegularConflict) {
            return ReservationPriorityDecision.reject();
        }

        List<Long> displacedReservationIds =
                conflicts.stream()
                        .map(ReservationSnapshot::id)
                        .toList();

        return ReservationPriorityDecision.displace(
                displacedReservationIds
        );
    }

    public boolean canRestore(
            ReservationSnapshot displacedReservation,
            List<ReservationSnapshot> activeReservations,
            boolean requesterIsActiveMember,
            ZonedDateTime now,
            ZonedDateTime applicationDeadline
    ) {
        if (displacedReservation == null) {
            throw new IllegalArgumentException(
                    "복구 대상 예약은 필수입니다."
            );
        }

        if (activeReservations == null) {
            throw new IllegalArgumentException(
                    "활성 예약 목록은 필수입니다."
            );
        }

        if (now == null || applicationDeadline == null) {
            throw new IllegalArgumentException(
                    "현재 시각과 신청 마감 시각은 필수입니다."
            );
        }

        if (displacedReservation.type()
                != ReservationType.STANDBY) {
            return false;
        }

        if (displacedReservation.status()
                != ReservationStatus.DISPLACED) {
            return false;
        }

        if (!requesterIsActiveMember) {
            return false;
        }

        if (!now.isBefore(applicationDeadline)) {
            return false;
        }

        if (!now.isBefore(displacedReservation.start())) {
            return false;
        }

        for (ReservationSnapshot activeReservation
                : activeReservations) {

            if (activeReservation == null) {
                continue;
            }

            if (activeReservation.id()
                    == displacedReservation.id()) {
                continue;
            }

            if (!activeReservation.isActive()) {
                continue;
            }

            if (!overlaps(
                    displacedReservation,
                    activeReservation
            )) {
                continue;
            }

            boolean sameRoom =
                    displacedReservation.roomId()
                            == activeReservation.roomId();

            boolean sameRequester =
                    displacedReservation.requesterId()
                            == activeReservation.requesterId();

            if (sameRoom || sameRequester) {
                return false;
            }
        }

        return true;
    }

    private List<ReservationSnapshot> findRoomConflicts(
            ReservationSnapshot newReservation,
            List<ReservationSnapshot> existingReservations
    ) {
        List<ReservationSnapshot> conflicts =
                new ArrayList<>();

        for (ReservationSnapshot existingReservation
                : existingReservations) {

            if (existingReservation == null) {
                continue;
            }

            if (!existingReservation.isActive()) {
                continue;
            }

            if (existingReservation.roomId()
                    != newReservation.roomId()) {
                continue;
            }

            if (!overlaps(
                    newReservation,
                    existingReservation
            )) {
                continue;
            }

            conflicts.add(existingReservation);
        }

        return conflicts;
    }

    private boolean overlaps(
            ReservationSnapshot first,
            ReservationSnapshot second
    ) {
        return first.start().isBefore(second.end())
                && second.start().isBefore(first.end());
    }
}