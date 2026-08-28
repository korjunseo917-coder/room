package com.cnu.practiceroom.reservation.application;

import com.cnu.practiceroom.club.domain.Club;
import com.cnu.practiceroom.club.repository.ClubRepository;
import com.cnu.practiceroom.reservation.domain.Reservation;
import com.cnu.practiceroom.reservation.domain.ReservationStatus;
import com.cnu.practiceroom.reservation.domain.ReservationType;
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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure
        .AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = NONE)
@Import({
        ApplyReservationService.class,
        DecideReservationService.class,
        DisplacedReservationRestorer.class,
        ReservationOperationLocker.class,
        ReservationLifecycleService.class,
        ReservationApplicationConfiguration.class
})
class ReservationLifecycleServiceTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    @Autowired
    private ApplyReservationService applyReservationService;

    @Autowired
    private DecideReservationService decideReservationService;

    @Autowired
    private ReservationLifecycleService lifecycleService;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("승인 대기 예약은 이용 시작 정각에 만료된다")
    void pendingReservationExpiresAtStart() {
        User member = createMember();

        ApplyReservationResult applied = applyStandby(
                member,
                findRoom("321"),
                time(2027, 5, 10, 10),
                time(2027, 5, 10, 12)
        );

        ZonedDateTime start = time(2027, 5, 10, 10);

        ReservationLifecycleResult result =
                lifecycleService.process(start);

        entityManager.clear();

        Reservation found = reservationRepository
                .findById(applied.reservationId())
                .orElseThrow();

        assertThat(found.getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);

        assertThat(found.getExpiredAt())
                .isEqualTo(start.toInstant());

        assertThat(result.expiredReservationIds())
                .contains(applied.reservationId());
    }

    @Test
    @DisplayName("승인된 예약은 이용 종료 정각에 완료된다")
    void approvedReservationCompletesAtEnd() {
        User member = createMember();
        User administrator = createAdministrator();

        ApplyReservationResult applied = applyStandby(
                member,
                findRoom("322"),
                time(2027, 5, 10, 10),
                time(2027, 5, 10, 12)
        );

        decideReservationService.decide(
                new DecideReservationCommand(
                        administrator.getId(),
                        applied.reservationId(),
                        AdminReservationDecision.APPROVE,
                        null,
                        time(2027, 5, 9, 21)
                )
        );

        ZonedDateTime end = time(2027, 5, 10, 12);

        ReservationLifecycleResult result =
                lifecycleService.process(end);

        entityManager.clear();

        Reservation found = reservationRepository
                .findById(applied.reservationId())
                .orElseThrow();

        assertThat(found.getStatus())
                .isEqualTo(ReservationStatus.COMPLETED);

        assertThat(found.getCompletedAt())
                .isEqualTo(end.toInstant());

        assertThat(result.completedReservationIds())
                .contains(applied.reservationId());
    }

    @Test
    @DisplayName("경계 전에는 승인 대기와 승인 상태를 변경하지 않는다")
    void lifecycleDoesNotChangeReservationsBeforeBoundaries() {
        User pendingMember = createMember();
        User approvedMember = createMember();
        User administrator = createAdministrator();

        ApplyReservationResult pending = applyStandby(
                pendingMember,
                findRoom("321"),
                time(2027, 5, 15, 10),
                time(2027, 5, 15, 12)
        );

        ApplyReservationResult approved = applyStandby(
                approvedMember,
                findRoom("322"),
                time(2027, 5, 15, 10),
                time(2027, 5, 15, 12)
        );

        decideReservationService.decide(
                new DecideReservationCommand(
                        administrator.getId(),
                        approved.reservationId(),
                        AdminReservationDecision.APPROVE,
                        null,
                        time(2027, 5, 14, 21)
                )
        );

        ReservationLifecycleResult result =
                lifecycleService.process(
                        time(2027, 5, 15, 9)
                );

        assertThat(result.expiredReservationIds())
                .doesNotContain(pending.reservationId());

        assertThat(result.completedReservationIds())
                .doesNotContain(approved.reservationId());

        assertThat(
                reservationRepository
                        .findById(pending.reservationId())
                        .orElseThrow()
                        .getStatus()
        ).isEqualTo(ReservationStatus.PENDING);

        assertThat(
                reservationRepository
                        .findById(approved.reservationId())
                        .orElseThrow()
                        .getStatus()
        ).isEqualTo(ReservationStatus.APPROVED);
    }

    private ApplyReservationResult applyStandby(
            User member,
            Room room,
            ZonedDateTime start,
            ZonedDateTime end
    ) {
        return applyReservationService.apply(
                new ApplyReservationCommand(
                        member.getId(),
                        room.getId(),
                        ReservationType.STANDBY,
                        start,
                        end,
                        start.minusDays(1).withHour(20)
                )
        );
    }

    private User createMember() {
        Club club = clubRepository.saveAndFlush(
                new Club(unique("상태전환테스트동아리"))
        );

        return userRepository.saveAndFlush(
                User.member(
                        unique("lifecycle-member"),
                        "temporary-password-hash",
                        "상태 전환 테스트 회원",
                        club
                )
        );
    }

    private User createAdministrator() {
        return userRepository.saveAndFlush(
                User.admin(
                        unique("lifecycle-admin"),
                        "temporary-password-hash",
                        "상태 전환 관리자"
                )
        );
    }

    private Room findRoom(String roomNumber) {
        return roomRepository.findByRoomNumber(roomNumber)
                .orElseThrow();
    }

    private String unique(String prefix) {
        return prefix
                + "-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "");
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
