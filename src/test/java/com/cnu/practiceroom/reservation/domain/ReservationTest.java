package com.cnu.practiceroom.reservation.domain;

import com.cnu.practiceroom.club.domain.Club;
import com.cnu.practiceroom.room.domain.Room;
import com.cnu.practiceroom.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

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
                        time(10),
                        time(11),
                        reservationPolicy
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비활성화된 사용자는 예약을 신청할 수 없습니다.");
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
                        time(10),
                        time(11),
                        reservationPolicy
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비활성화된 연습실에는 예약을 신청할 수 없습니다.");
    }

    private ZonedDateTime time(int hour) {
        return ZonedDateTime.of(
                2026,
                9,
                10,
                hour,
                0,
                0,
                0,
                SEOUL
        );
    }
}
