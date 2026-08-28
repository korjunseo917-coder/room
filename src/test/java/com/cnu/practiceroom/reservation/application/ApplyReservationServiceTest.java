package com.cnu.practiceroom.reservation.application;

import com.cnu.practiceroom.club.domain.Club;
import com.cnu.practiceroom.club.repository.ClubRepository;
import com.cnu.practiceroom.reservation.domain.ClubMonthlyQuota;
import com.cnu.practiceroom.reservation.domain.Reservation;
import com.cnu.practiceroom.reservation.domain.ReservationStatus;
import com.cnu.practiceroom.reservation.domain.ReservationType;
import com.cnu.practiceroom.reservation.repository.ClubMonthlyQuotaRepository;
import com.cnu.practiceroom.reservation.repository.ReservationRepository;
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
import org.springframework.context.annotation.Import;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure
        .AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = NONE)
@Import({
        ApplyReservationService.class,
        ReservationApplicationConfiguration.class
})
class ApplyReservationServiceTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    @Autowired
    private ApplyReservationService applyReservationService;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ClubMonthlyQuotaRepository quotaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("빈 시간의 대기예약은 월간 쿼터 없이 승인 대기로 접수한다")
    void appliesStandbyReservationWithoutMonthlyQuota() {
        User member = createMember(createClub());
        Room room = findRoom("321");

        ApplyReservationResult result =
                applyReservationService.apply(
                        command(
                                member,
                                room,
                                ReservationType.STANDBY,
                                time(2027, 1, 10, 10),
                                time(2027, 1, 10, 12),
                                time(2027, 1, 9, 20)
                        )
                );

        Reservation saved = reservationRepository
                .findById(result.reservationId())
                .orElseThrow();

        assertThat(saved.getStatus())
                .isEqualTo(ReservationStatus.PENDING);

        assertThat(saved.getType())
                .isEqualTo(ReservationType.STANDBY);

        assertThat(saved.getUsageMinutesByMonth())
                .isEmpty();
    }

    @Test
    @DisplayName("기존 활성예약과 겹치는 신규 대기예약은 거절한다")
    void rejectsOverlappingStandbyReservation() {
        User firstMember = createMember(createClub());
        User secondMember = createMember(createClub());
        Room room = findRoom("321");

        applyReservationService.apply(
                command(
                        firstMember,
                        room,
                        ReservationType.STANDBY,
                        time(2027, 1, 10, 10),
                        time(2027, 1, 10, 12),
                        time(2027, 1, 9, 20)
                )
        );

        assertThatThrownBy(
                () -> applyReservationService.apply(
                        command(
                                secondMember,
                                room,
                                ReservationType.STANDBY,
                                time(2027, 1, 10, 11),
                                time(2027, 1, 10, 13),
                                time(2027, 1, 9, 21)
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "해당 연습실 시간대에 이미 활성 예약이 있습니다."
                );
    }

    @Test
    @DisplayName("신규 정규예약은 겹치는 대기예약을 신청 즉시 밀어낸다")
    void regularReservationDisplacesStandbyImmediately() {
        User standbyMember = createMember(createClub());

        Club regularClub = createClub();
        User regularMember = createMember(regularClub);
        saveQuota(regularClub, YearMonth.of(2027, 1), 10 * 60L);

        Room room = findRoom("322");

        ApplyReservationResult standbyResult =
                applyReservationService.apply(
                        command(
                                standbyMember,
                                room,
                                ReservationType.STANDBY,
                                time(2027, 1, 15, 10),
                                time(2027, 1, 15, 12),
                                time(2027, 1, 14, 20)
                        )
                );

        ApplyReservationResult regularResult =
                applyReservationService.apply(
                        command(
                                regularMember,
                                room,
                                ReservationType.REGULAR,
                                time(2027, 1, 15, 10),
                                time(2027, 1, 15, 12),
                                time(2027, 1, 14, 21)
                        )
                );

        entityManager.clear();

        Reservation displacedStandby = reservationRepository
                .findById(standbyResult.reservationId())
                .orElseThrow();

        Reservation regularReservation = reservationRepository
                .findById(regularResult.reservationId())
                .orElseThrow();

        assertThat(displacedStandby.getStatus())
                .isEqualTo(ReservationStatus.DISPLACED);

        assertThat(
                displacedStandby
                        .getStatusBeforeDisplacement()
        ).isEqualTo(ReservationStatus.PENDING);

        assertThat(displacedStandby.getDisplacedBy().getId())
                .isEqualTo(regularReservation.getId());

        assertThat(regularResult.displacedReservationIds())
                .containsExactly(displacedStandby.getId());
    }

    @Test
    @DisplayName("승인 대기 정규예약도 동아리 월간 허용시간에 포함한다")
    void pendingRegularReservationConsumesMonthlyQuota() {
        Club club = createClub();
        User firstMember = createMember(club);
        User secondMember = createMember(club);

        saveQuota(club, YearMonth.of(2027, 1), 2 * 60L);

        applyReservationService.apply(
                command(
                        firstMember,
                        findRoom("321"),
                        ReservationType.REGULAR,
                        time(2027, 1, 10, 10),
                        time(2027, 1, 10, 12),
                        time(2027, 1, 9, 20)
                )
        );

        assertThatThrownBy(
                () -> applyReservationService.apply(
                        command(
                                secondMember,
                                findRoom("322"),
                                ReservationType.REGULAR,
                                time(2027, 1, 11, 10),
                                time(2027, 1, 11, 11),
                                time(2027, 1, 10, 20)
                        )
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "2027-01의 월간 정규시간이 부족합니다."
                );
    }

    @Test
    @DisplayName("월간 허용시간이 없는 운영월의 정규예약은 거절한다")
    void rejectsRegularReservationWithoutMonthlyQuota() {
        User member = createMember(createClub());
        Room room = findRoom("321");

        assertThatThrownBy(
                () -> applyReservationService.apply(
                        command(
                                member,
                                room,
                                ReservationType.REGULAR,
                                time(2027, 2, 10, 10),
                                time(2027, 2, 10, 11),
                                time(2027, 2, 9, 20)
                        )
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "2027-02의 월간 허용시간이 설정되지 않았습니다."
                );
    }

    @Test
    @DisplayName("같은 사용자의 다른 연습실 동시간 예약은 신청 단계에서 거절한다")
    void rejectsSameRequesterInDifferentRoom() {
        User member = createMember(createClub());

        applyReservationService.apply(
                command(
                        member,
                        findRoom("321"),
                        ReservationType.STANDBY,
                        time(2027, 2, 15, 10),
                        time(2027, 2, 15, 12),
                        time(2027, 2, 14, 20)
                )
        );

        assertThatThrownBy(
                () -> applyReservationService.apply(
                        command(
                                member,
                                findRoom("322"),
                                ReservationType.STANDBY,
                                time(2027, 2, 15, 11),
                                time(2027, 2, 15, 13),
                                time(2027, 2, 14, 21)
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "같은 사용자는 다른 연습실을 동일 시간에 예약할 수 없습니다."
                );
    }

    private Club createClub() {
        return clubRepository.saveAndFlush(
                new Club(unique("신청서비스테스트동아리"))
        );
    }

    private User createMember(Club club) {
        return userRepository.saveAndFlush(
                User.member(
                        unique("apply-member"),
                        "temporary-password-hash",
                        "예약 신청 테스트 회원",
                        club
                )
        );
    }

    private void saveQuota(
            Club club,
            YearMonth month,
            long quotaMinutes
    ) {
        quotaRepository.saveAndFlush(
                new ClubMonthlyQuota(
                        club,
                        month,
                        quotaMinutes
                )
        );
    }

    private Room findRoom(String roomNumber) {
        return roomRepository.findByRoomNumber(roomNumber)
                .orElseThrow();
    }

    private ApplyReservationCommand command(
            User member,
            Room room,
            ReservationType type,
            ZonedDateTime start,
            ZonedDateTime end,
            ZonedDateTime requestedAt
    ) {
        return new ApplyReservationCommand(
                member.getId(),
                room.getId(),
                type,
                start,
                end,
                requestedAt
        );
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
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
