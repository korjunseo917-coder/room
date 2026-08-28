package com.cnu.practiceroom.reservation.domain;

import com.cnu.practiceroom.club.domain.Club;
import com.cnu.practiceroom.room.domain.Room;
import com.cnu.practiceroom.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    private final ReservationPolicy reservationPolicy =
            new ReservationPolicy();

    @Test
    @DisplayName("비활성화된 회원은 예약을 신청할 수 없다")
    void inactiveMemberCannotRequestReservation() {
        Club club = new Club("테스트 동아리");

        User member = User.member(
                "inactive-member",
                "temporary-password-hash",
                "비활성 회원",
                club
        );

        member.deactivate();

        Room room = new Room(
                "321",
                "321호 연습실"
        );

        assertThatThrownBy(
                () -> Reservation.pending(
                        member,
                        room,
                        ReservationType.REGULAR,
                        time(2026, 9, 10, 10, 0),
                        time(2026, 9, 10, 11, 0),
                        time(2026, 9, 9, 21, 0),
                        reservationPolicy
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "비활성화된 사용자는 예약을 신청할 수 없습니다."
                );
    }

    @Test
    @DisplayName("비활성화된 연습실에는 예약을 신청할 수 없다")
    void inactiveRoomCannotReceiveReservation() {
        Club club = new Club("테스트 동아리");

        User member = User.member(
                "active-member",
                "temporary-password-hash",
                "활성 회원",
                club
        );

        Room room = new Room(
                "322",
                "322호 연습실"
        );

        room.deactivate();

        assertThatThrownBy(
                () -> Reservation.pending(
                        member,
                        room,
                        ReservationType.REGULAR,
                        time(2026, 9, 10, 10, 0),
                        time(2026, 9, 10, 11, 0),
                        time(2026, 9, 9, 21, 0),
                        reservationPolicy
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "비활성화된 연습실에는 예약을 신청할 수 없습니다."
                );
    }

    @Test
    @DisplayName("운영월 예약이 열리기 전에는 신청할 수 없다")
    void cannotRequestBeforeMonthlyOpening() {
        User member = activeMember();
        Room room = activeRoom();

        /*
         * 9월 운영월은 8월 27일 20시에 열린다.
         * 그보다 1분 전인 19시 59분 신청은 거절되어야 한다.
         */
        assertThatThrownBy(
                () -> Reservation.pending(
                        member,
                        room,
                        ReservationType.REGULAR,
                        time(2026, 9, 10, 10, 0),
                        time(2026, 9, 10, 11, 0),
                        time(2026, 8, 27, 19, 59),
                        reservationPolicy
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("아직 예약 신청 기간이 아닙니다.");
    }

    @Test
    @DisplayName("운영월 예약이 열리는 정확한 시각부터 신청할 수 있다")
    void canRequestAtMonthlyOpening() {
        User member = activeMember();
        Room room = activeRoom();

        assertThatCode(
                () -> Reservation.pending(
                        member,
                        room,
                        ReservationType.REGULAR,
                        time(2026, 9, 10, 10, 0),
                        time(2026, 9, 10, 11, 0),
                        time(2026, 8, 27, 20, 0),
                        reservationPolicy
                )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("신청 마감 시각과 정확히 같으면 신청할 수 없다")
    void cannotRequestAtApplicationDeadline() {
        User member = activeMember();
        Room room = activeRoom();

        /*
         * 9월 10일 운영일 예약의 마감은
         * 전날인 9월 9일 22시다.
         */
        assertThatThrownBy(
                () -> Reservation.pending(
                        member,
                        room,
                        ReservationType.REGULAR,
                        time(2026, 9, 10, 10, 0),
                        time(2026, 9, 10, 11, 0),
                        time(2026, 9, 9, 22, 0),
                        reservationPolicy
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("예약 신청 기간이 마감되었습니다.");
    }

    @Test
    @DisplayName("신청자는 이용 시작 전에 예약을 취소할 수 있다")
    void requesterCanCancelBeforeStart() {
        User member = activeMember();
        Room room = activeRoom();

        Reservation reservation = Reservation.pending(
                member,
                room,
                ReservationType.REGULAR,
                time(2026, 9, 10, 10, 0),
                time(2026, 9, 10, 12, 0),
                time(2026, 9, 9, 20, 0),
                reservationPolicy
        );

        ZonedDateTime canceledAt =
                time(2026, 9, 10, 9, 0);

        reservation.cancelByRequester(
                canceledAt,
                new ReservationStatePolicy()
        );

        assertThat(reservation.getStatus())
                .isEqualTo(ReservationStatus.CANCELED);

        assertThat(reservation.getCanceledAt())
                .isEqualTo(canceledAt.toInstant());
    }

    @Test
    @DisplayName("신청자는 이용 시작 시각부터 직접 취소할 수 없다")
    void requesterCannotCancelAtStart() {
        User member = activeMember();
        Room room = activeRoom();

        Reservation reservation = Reservation.pending(
                member,
                room,
                ReservationType.REGULAR,
                time(2026, 9, 10, 10, 0),
                time(2026, 9, 10, 12, 0),
                time(2026, 9, 9, 20, 0),
                reservationPolicy
        );

        assertThatThrownBy(
                () -> reservation.cancelByRequester(
                        time(2026, 9, 10, 10, 0),
                        new ReservationStatePolicy()
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이용 시작 이후에는 신청자가 취소할 수 없습니다."
                );

        /*
         * 실패한 취소가 예약 상태를 변경하면 안 된다.
         */
        assertThat(reservation.getStatus())
                .isEqualTo(ReservationStatus.PENDING);

        assertThat(reservation.getCanceledAt())
                .isNull();
    }

    private User activeMember() {
        Club club = new Club("테스트 동아리");

        return User.member(
                "active-member",
                "temporary-password-hash",
                "활성 회원",
                club
        );
    }

    private Room activeRoom() {
        return new Room(
                "321",
                "321호 연습실"
        );
    }

    private ZonedDateTime time(
            int year,
            int month,
            int day,
            int hour,
            int minute
    ) {
        return ZonedDateTime.of(
                year,
                month,
                day,
                hour,
                minute,
                0,
                0,
                SEOUL
        );
    }
}