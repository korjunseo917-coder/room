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
        DecideReservationService.class,
        DisplacedReservationRestorer.class,
        ReservationOperationLocker.class,
        ReservationApplicationConfiguration.class
})
class DecideReservationServiceTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    @Autowired
    private ApplyReservationService applyReservationService;

    @Autowired
    private DecideReservationService decideReservationService;

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
    @DisplayName("관리자는 이용 시작 전에 승인 대기 예약을 승인한다")
    void administratorApprovesPendingReservation() {
        User member = createMember(createClub());
        User administrator = createAdministrator();

        ApplyReservationResult applied = applyStandby(
                member,
                findRoom("321"),
                time(2027, 4, 9, 20),
                time(2027, 4, 10, 10),
                time(2027, 4, 10, 12)
        );

        ZonedDateTime decidedAt =
                time(2027, 4, 9, 21);

        DecideReservationResult result =
                decideReservationService.decide(
                        new DecideReservationCommand(
                                administrator.getId(),
                                applied.reservationId(),
                                AdminReservationDecision.APPROVE,
                                null,
                                decidedAt
                        )
                );

        entityManager.clear();

        Reservation found = reservationRepository
                .findById(result.reservationId())
                .orElseThrow();

        assertThat(found.getStatus())
                .isEqualTo(ReservationStatus.APPROVED);

        assertThat(found.getDecidedBy().getId())
                .isEqualTo(administrator.getId());

        assertThat(found.getDecidedAt())
                .isEqualTo(decidedAt.toInstant());

        assertThat(found.getRejectionReason()).isNull();
    }

    @Test
    @DisplayName("일반 회원은 예약을 승인할 수 없다")
    void memberCannotApproveReservation() {
        User requester = createMember(createClub());
        User otherMember = createMember(createClub());

        ApplyReservationResult applied = applyStandby(
                requester,
                findRoom("321"),
                time(2027, 4, 9, 20),
                time(2027, 4, 10, 10),
                time(2027, 4, 10, 12)
        );

        assertThatThrownBy(
                () -> decideReservationService.decide(
                        new DecideReservationCommand(
                                otherMember.getId(),
                                applied.reservationId(),
                                AdminReservationDecision.APPROVE,
                                null,
                                time(2027, 4, 9, 21)
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "관리자만 예약을 승인하거나 거절할 수 있습니다."
                );
    }

    @Test
    @DisplayName("관리자가 예약을 거절할 때 거절 사유는 필수다")
    void rejectionReasonIsRequired() {
        User member = createMember(createClub());
        User administrator = createAdministrator();

        ApplyReservationResult applied = applyStandby(
                member,
                findRoom("321"),
                time(2027, 4, 9, 20),
                time(2027, 4, 10, 10),
                time(2027, 4, 10, 12)
        );

        assertThatThrownBy(
                () -> decideReservationService.decide(
                        new DecideReservationCommand(
                                administrator.getId(),
                                applied.reservationId(),
                                AdminReservationDecision.REJECT,
                                "   ",
                                time(2027, 4, 9, 21)
                        )
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("거절 사유는 필수입니다.");
    }

    @Test
    @DisplayName("정규예약을 거절하면 밀려난 대기예약을 조건부 복구한다")
    void rejectionRestoresDisplacedStandby() {
        User standbyMember = createMember(createClub());

        Club regularClub = createClub();
        User regularMember = createMember(regularClub);
        saveQuota(regularClub, YearMonth.of(2027, 4), 10 * 60L);

        User administrator = createAdministrator();
        Room room = findRoom("321");

        ApplyReservationResult standby = applyStandby(
                standbyMember,
                room,
                time(2027, 4, 9, 20),
                time(2027, 4, 10, 10),
                time(2027, 4, 10, 12)
        );

        ApplyReservationResult regular = applyRegular(
                regularMember,
                room,
                time(2027, 4, 9, 21),
                time(2027, 4, 10, 10),
                time(2027, 4, 10, 12)
        );

        ZonedDateTime rejectedAt =
                time(2027, 4, 10, 9);

        DecideReservationResult result =
                decideReservationService.decide(
                        new DecideReservationCommand(
                                administrator.getId(),
                                regular.reservationId(),
                                AdminReservationDecision.REJECT,
                                "동아리 일정 확인 불가",
                                rejectedAt
                        )
                );

        entityManager.clear();

        Reservation rejectedRegular = reservationRepository
                .findById(regular.reservationId())
                .orElseThrow();

        Reservation restoredStandby = reservationRepository
                .findById(standby.reservationId())
                .orElseThrow();

        assertThat(rejectedRegular.getStatus())
                .isEqualTo(ReservationStatus.REJECTED);

        assertThat(rejectedRegular.getRejectionReason())
                .isEqualTo("동아리 일정 확인 불가");

        assertThat(rejectedRegular.getDecidedBy().getId())
                .isEqualTo(administrator.getId());

        assertThat(restoredStandby.getStatus())
                .isEqualTo(ReservationStatus.PENDING);

        assertThat(result.restoredReservationIds())
                .containsExactly(restoredStandby.getId());
    }

    @Test
    @DisplayName("이용 시작 정각부터 관리자는 승인하거나 거절할 수 없다")
    void administratorCannotDecideAtStart() {
        User member = createMember(createClub());
        User administrator = createAdministrator();

        ApplyReservationResult applied = applyStandby(
                member,
                findRoom("321"),
                time(2027, 4, 9, 20),
                time(2027, 4, 10, 10),
                time(2027, 4, 10, 12)
        );

        assertThatThrownBy(
                () -> decideReservationService.decide(
                        new DecideReservationCommand(
                                administrator.getId(),
                                applied.reservationId(),
                                AdminReservationDecision.APPROVE,
                                null,
                                time(2027, 4, 10, 10)
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이용 시작 이후에는 승인할 수 없습니다."
                );
    }

    @Test
    @DisplayName("거절된 정규예약은 월간 허용시간에서 제외한다")
    void rejectedRegularReservationReleasesMonthlyQuota() {
        Club club = createClub();
        User firstMember = createMember(club);
        User secondMember = createMember(club);
        User administrator = createAdministrator();

        saveQuota(club, YearMonth.of(2027, 4), 60L);

        ApplyReservationResult first = applyRegular(
                firstMember,
                findRoom("321"),
                time(2027, 4, 9, 20),
                time(2027, 4, 10, 10),
                time(2027, 4, 10, 11)
        );

        decideReservationService.decide(
                new DecideReservationCommand(
                        administrator.getId(),
                        first.reservationId(),
                        AdminReservationDecision.REJECT,
                        "사용 계획 불충분",
                        time(2027, 4, 9, 21)
                )
        );

        ApplyReservationResult second = applyRegular(
                secondMember,
                findRoom("322"),
                time(2027, 4, 10, 20),
                time(2027, 4, 11, 10),
                time(2027, 4, 11, 11)
        );

        assertThat(second.status())
                .isEqualTo(ReservationStatus.PENDING);
    }

    private ApplyReservationResult applyStandby(
            User member,
            Room room,
            ZonedDateTime requestedAt,
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
                        requestedAt
                )
        );
    }

    private ApplyReservationResult applyRegular(
            User member,
            Room room,
            ZonedDateTime requestedAt,
            ZonedDateTime start,
            ZonedDateTime end
    ) {
        return applyReservationService.apply(
                new ApplyReservationCommand(
                        member.getId(),
                        room.getId(),
                        ReservationType.REGULAR,
                        start,
                        end,
                        requestedAt
                )
        );
    }

    private Club createClub() {
        return clubRepository.saveAndFlush(
                new Club(unique("관리자처리테스트동아리"))
        );
    }

    private User createMember(Club club) {
        return userRepository.saveAndFlush(
                User.member(
                        unique("decision-member"),
                        "temporary-password-hash",
                        "예약 처리 테스트 회원",
                        club
                )
        );
    }

    private User createAdministrator() {
        return userRepository.saveAndFlush(
                User.admin(
                        unique("decision-admin"),
                        "temporary-password-hash",
                        "예약 처리 관리자"
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
