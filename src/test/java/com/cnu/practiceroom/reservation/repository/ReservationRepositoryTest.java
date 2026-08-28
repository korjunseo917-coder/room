package com.cnu.practiceroom.reservation.repository;

import com.cnu.practiceroom.club.domain.Club;
import com.cnu.practiceroom.club.repository.ClubRepository;
import com.cnu.practiceroom.reservation.domain.Reservation;
import com.cnu.practiceroom.reservation.domain.ReservationPolicy;
import com.cnu.practiceroom.reservation.domain.ReservationStatus;
import com.cnu.practiceroom.reservation.domain.ReservationType;
import com.cnu.practiceroom.room.domain.Room;
import com.cnu.practiceroom.room.repository.RoomRepository;
import com.cnu.practiceroom.user.domain.User;
import com.cnu.practiceroom.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure
        .AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = NONE)
class ReservationRepositoryTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    /*
     * 현재 운영 규칙이 들어 있는 기본 예약 정책이다.
     *
     * Reservation이 정책을 직접 생성하지 않고,
     * 예약을 만드는 쪽에서 정책을 전달한다.
     */
    private final ReservationPolicy reservationPolicy =
            new ReservationPolicy();

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("자정과 월 경계를 지나는 예약을 9월 예약으로 저장한다")
    void savesReservationAcrossMonthBoundary() {
        User member = createMember();
        Room room = findRoom("321");

        Reservation saved = reservationRepository.saveAndFlush(
                Reservation.pending(
                        member,
                        room,
                        ReservationType.REGULAR,
                        time(2026, 9, 30, 23),
                        time(2026, 10, 1, 2),
                        time(2026, 9, 29, 21),
                        reservationPolicy
                )
        );

        entityManager.clear();

        Reservation found = reservationRepository
                .findById(saved.getId())
                .orElseThrow();

        assertThat(found.getStatus())
                .isEqualTo(ReservationStatus.PENDING);

        assertThat(found.getType())
                .isEqualTo(ReservationType.REGULAR);

        assertThat(found.getUsageMonth())
                .isEqualTo(LocalDate.of(2026, 9, 1));

        assertThat(found.getRoom().getRoomNumber())
                .isEqualTo("321");

        assertThat(found.toSnapshot().start())
                .isEqualTo(time(2026, 9, 30, 23));

        assertThat(found.toSnapshot().end())
                .isEqualTo(time(2026, 10, 1, 2));
    }

    @Test
    @DisplayName("승인 대기 예약과 시간이 겹치면 DB가 거절한다")
    void rejectsOverlappingPendingReservation() {
        User member = createMember();
        Room room = findRoom("322");

        reservationRepository.saveAndFlush(
                Reservation.pending(
                        member,
                        room,
                        ReservationType.STANDBY,
                        time(2026, 11, 10, 10),
                        time(2026, 11, 10, 12),
                        time(2026, 11, 9, 20),
                        reservationPolicy
                )
        );

        Reservation overlapping = Reservation.pending(
                member,
                room,
                ReservationType.STANDBY,
                time(2026, 11, 10, 11),
                time(2026, 11, 10, 13),
                time(2026, 11, 9, 21),
                reservationPolicy
        );

        assertThatThrownBy(
                () -> reservationRepository.saveAndFlush(overlapping)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 사용자는 서로 다른 연습실을 같은 시간에 예약할 수 없다")

    void rejectsSameRequesterOverlappingDifferentRooms() {
        User member = createMember();

        Room room321 = findRoom("321");
        Room room322 = findRoom("322");

        reservationRepository.saveAndFlush(
                Reservation.pending(
                        member,
                        room321,
                        ReservationType.REGULAR,
                        time(2026, 12, 10, 10),
                        time(2026, 12, 10, 12),
                        time(2026, 12, 9, 20),
                        reservationPolicy
                )
        );

        Reservation overlapping = Reservation.pending(
                member,
                room322,
                ReservationType.STANDBY,
                time(2026, 12, 10, 11),
                time(2026, 12, 10, 13),
                time(2026, 12, 9, 21),
                reservationPolicy
        );

        assertThatThrownBy(
                () -> reservationRepository.saveAndFlush(overlapping)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("서로 다른 사용자는 서로 다른 연습실을 같은 시간에 예약할 수 있다")
    void allowsDifferentRequestersInDifferentRooms() {
        User firstMember = createMember();
        User secondMember = createMember();

        Room room321 = findRoom("321");
        Room room322 = findRoom("322");

        Reservation firstReservation =
                reservationRepository.saveAndFlush(
                        Reservation.pending(
                                firstMember,
                                room321,
                                ReservationType.REGULAR,
                                time(2026, 12, 15, 10),
                                time(2026, 12, 15, 12),
                                time(2026, 12, 14, 20),
                                reservationPolicy
                        )
                );

        Reservation secondReservation =
                reservationRepository.saveAndFlush(
                        Reservation.pending(
                                secondMember,
                                room322,
                                ReservationType.REGULAR,
                                time(2026, 12, 15, 10),
                                time(2026, 12, 15, 12),
                                time(2026, 12, 14, 20),
                                reservationPolicy
                        )
                );

        assertThat(firstReservation.getId()).isNotNull();
        assertThat(secondReservation.getId()).isNotNull();

        assertThat(firstReservation.getStatus())
                .isEqualTo(ReservationStatus.PENDING);

        assertThat(secondReservation.getStatus())
                .isEqualTo(ReservationStatus.PENDING);
    }

    private User createMember() {
        String uniqueValue = UUID.randomUUID().toString();

        Club club = clubRepository.saveAndFlush(
                new Club("예약테스트동아리-" + uniqueValue)
        );

        return userRepository.saveAndFlush(
                User.member(
                        "reservation-" + uniqueValue,
                        "temporary-password-hash",
                        "예약 테스트 회원",
                        club
                )
        );
    }

    private Room findRoom(String roomNumber) {
        return roomRepository.findByRoomNumber(roomNumber)
                .orElseThrow();
    }

    private ZonedDateTime time(
            int year,
            int month,
            int day,
            int hour
    ) {
        return ZonedDateTime.of(
                year,
                month,
                day,
                hour,
                0,
                0,
                0,
                SEOUL
        );
    }
}