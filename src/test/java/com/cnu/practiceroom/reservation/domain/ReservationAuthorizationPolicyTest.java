package com.cnu.practiceroom.reservation.domain;

import com.cnu.practiceroom.user.domain.UserRole;
import com.cnu.practiceroom.user.domain.UserSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationAuthorizationPolicyTest {

    private final ReservationAuthorizationPolicy policy =
            new ReservationAuthorizationPolicy();

    @Test
    @DisplayName("활성 부원은 자신의 동아리 예약을 신청할 수 있다")
    void activeMemberCanApplyForOwnClub() {
        UserSnapshot member =
                activeMember(100, 1);

        assertDoesNotThrow(
                () -> policy.validateCanApply(
                        member,
                        1
                )
        );
    }

    @Test
    @DisplayName("비활성 부원은 신규 예약을 신청할 수 없다")
    void inactiveMemberCannotApply() {
        UserSnapshot member = new UserSnapshot(
                100,
                1L,
                Set.of(UserRole.MEMBER),
                false
        );

        assertThrows(
                IllegalStateException.class,
                () -> policy.validateCanApply(
                        member,
                        1
                )
        );
    }

    @Test
    @DisplayName("다른 동아리 이름으로 예약을 신청할 수 없다")
    void memberCannotApplyForOtherClub() {
        UserSnapshot member =
                activeMember(100, 1);

        assertThrows(
                IllegalStateException.class,
                () -> policy.validateCanApply(
                        member,
                        2
                )
        );
    }

    @Test
    @DisplayName("관리자 역할만 있는 사용자는 일반 예약을 신청할 수 없다")
    void adminWithoutMemberRoleCannotApply() {
        UserSnapshot admin =
                admin(900);

        assertThrows(
                IllegalStateException.class,
                () -> policy.validateCanApply(
                        admin,
                        1
                )
        );
    }

    @Test
    @DisplayName("공통 관리자는 모든 동아리의 예약을 승인할 수 있다")
    void commonAdminCanApproveBothClubs() {
        UserSnapshot admin =
                admin(900);

        assertDoesNotThrow(
                () -> policy.validateCanApproveOrReject(admin)
        );
    }

    @Test
    @DisplayName("일반 부원은 예약을 승인하거나 거절할 수 없다")
    void memberCannotApproveOrReject() {
        UserSnapshot member =
                activeMember(100, 1);

        assertThrows(
                IllegalStateException.class,
                () -> policy.validateCanApproveOrReject(
                        member
                )
        );
    }

    @Test
    @DisplayName("신청자는 비활성 상태여도 자신의 기존 예약을 취소할 수 있다")
    void inactiveRequesterCanCancelOwnReservation() {
        UserSnapshot inactiveRequester =
                new UserSnapshot(
                        100,
                        1L,
                        Set.of(UserRole.MEMBER),
                        false
                );

        ReservationAccessSnapshot reservation =
                reservation(10, 100, 1);

        assertDoesNotThrow(
                () -> policy.validateCanCancel(
                        inactiveRequester,
                        reservation
                )
        );
    }

    @Test
    @DisplayName("다른 부원의 예약을 취소할 수 없다")
    void memberCannotCancelOtherMembersReservation() {
        UserSnapshot member =
                activeMember(100, 1);

        ReservationAccessSnapshot reservation =
                reservation(10, 200, 1);

        assertThrows(
                IllegalStateException.class,
                () -> policy.validateCanCancel(
                        member,
                        reservation
                )
        );
    }

    @Test
    @DisplayName("관리자는 모든 사용자의 예약을 취소할 수 있다")
    void adminCanCancelAnyReservation() {
        UserSnapshot admin =
                admin(900);

        ReservationAccessSnapshot reservation =
                reservation(10, 200, 2);

        assertDoesNotThrow(
                () -> policy.validateCanCancel(
                        admin,
                        reservation
                )
        );
    }

    @Test
    @DisplayName("신청자는 자신의 예약자 정보를 볼 수 있다")
    void requesterCanViewOwnIdentity() {
        UserSnapshot requester =
                activeMember(100, 1);

        ReservationAccessSnapshot reservation =
                reservation(10, 100, 1);

        assertTrue(
                policy.canViewRequesterIdentity(
                        requester,
                        reservation
                )
        );
    }

    @Test
    @DisplayName("관리자는 모든 예약자의 정보를 볼 수 있다")
    void adminCanViewAllRequesterIdentities() {
        UserSnapshot admin =
                admin(900);

        ReservationAccessSnapshot reservation =
                reservation(10, 200, 2);

        assertTrue(
                policy.canViewRequesterIdentity(
                        admin,
                        reservation
                )
        );
    }

    @Test
    @DisplayName("일반 부원은 다른 신청자의 정보를 볼 수 없다")
    void memberCannotViewOtherRequesterIdentity() {
        UserSnapshot member =
                activeMember(100, 1);

        ReservationAccessSnapshot reservation =
                reservation(10, 200, 1);

        assertFalse(
                policy.canViewRequesterIdentity(
                        member,
                        reservation
                )
        );
    }

    private UserSnapshot activeMember(
            long userId,
            long clubId
    ) {
        return new UserSnapshot(
                userId,
                clubId,
                Set.of(UserRole.MEMBER),
                true
        );
    }

    private UserSnapshot admin(long userId) {
        return new UserSnapshot(
                userId,
                null,
                Set.of(UserRole.ADMIN),
                false
        );
    }

    private ReservationAccessSnapshot reservation(
            long reservationId,
            long requesterId,
            long clubId
    ) {
        return new ReservationAccessSnapshot(
                reservationId,
                requesterId,
                clubId
        );
    }
}