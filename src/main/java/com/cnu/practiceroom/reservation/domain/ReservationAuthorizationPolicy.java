package com.cnu.practiceroom.reservation.domain;

import com.cnu.practiceroom.user.domain.UserRole;
import com.cnu.practiceroom.user.domain.UserSnapshot;

import java.util.Objects;

public final class ReservationAuthorizationPolicy {

    public void validateCanApply(
            UserSnapshot user,
            long targetClubId
    ) {
        requireUser(user);

        if (targetClubId <= 0) {
            throw new IllegalArgumentException(
                    "대상 동아리 ID는 0보다 커야 합니다."
            );
        }

        if (!user.hasRole(UserRole.MEMBER)) {
            throw new IllegalStateException(
                    "동아리 부원만 예약을 신청할 수 있습니다."
            );
        }

        if (!user.activeMembership()) {
            throw new IllegalStateException(
                    "활성 동아리 부원만 예약을 신청할 수 있습니다."
            );
        }

        if (!Objects.equals(user.clubId(), targetClubId)) {
            throw new IllegalStateException(
                    "자신이 소속된 동아리의 예약만 신청할 수 있습니다."
            );
        }
    }

    public void validateCanApproveOrReject(
            UserSnapshot user
    ) {
        requireUser(user);

        if (!user.hasRole(UserRole.ADMIN)) {
            throw new IllegalStateException(
                    "관리자만 예약을 승인하거나 거절할 수 있습니다."
            );
        }
    }

    public void validateCanCancel(
            UserSnapshot user,
            ReservationAccessSnapshot reservation
    ) {
        requireUser(user);
        requireReservation(reservation);

        if (user.hasRole(UserRole.ADMIN)) {
            return;
        }

        if (user.id() != reservation.requesterId()) {
            throw new IllegalStateException(
                    "자신이 신청한 예약만 취소할 수 있습니다."
            );
        }
    }

    public boolean canViewRequesterIdentity(
            UserSnapshot user,
            ReservationAccessSnapshot reservation
    ) {
        requireUser(user);
        requireReservation(reservation);

        if (user.hasRole(UserRole.ADMIN)) {
            return true;
        }

        return user.id() == reservation.requesterId();
    }

    private void requireUser(UserSnapshot user) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "사용자는 필수입니다."
            );
        }
    }

    private void requireReservation(
            ReservationAccessSnapshot reservation
    ) {
        if (reservation == null) {
            throw new IllegalArgumentException(
                    "예약 정보는 필수입니다."
            );
        }
    }
}