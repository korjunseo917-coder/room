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
        CancelReservationService.class,
        DisplacedReservationRestorer.class,
        ReservationApplicationConfiguration.class
})
class CancelReservationServiceTest {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    @Autowired
    private ApplyReservationService applyReservationService;

    @Autowired
    private CancelReservationService cancelReservationService;

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
    @DisplayName("신청자는 자신의 예약을 이용 시작 전에 취소한다")
    void requesterCancelsOwnReservationBeforeStart() {
        User member = createMember(createClub());

        ApplyReservationResult applied = applyStandby(
                member,
                findRoom("321"),
                20,
                10,
                12
        );

        CancelReservationResult canceled =
                cancelReservationService.cancel(
                        new CancelReservationCommand(
                                member.getId(),
                                applied.reservationId(),
                                time(2027, 3, 19, 21)
                        )
                );

        entityManager.clear();

        Reservation found = reservationRepository
                .findById(canceled.reservationId())
                .orElseThrow();

        assertThat(found.getStatus())
                .isEqualTo(ReservationStatus.CANCELED);

        assertThat(found.getCanceledAt())
                .isEqualTo(time(2027, 3, 19, 21).toInstant());
    }

    @Test
    @DisplayName("다른 회원은 신청자의 예약을 취소할 수 없다")
    void otherMemberCannotCancelReservation() {
        User requester = createMember(createClub());
        User otherMember = createMember(createClub());

        ApplyReservationResult applied = applyStandby(
                requester,
                findRoom("321"),
                20,
                10,
                12
        );

        assertThatThrownBy(
                () -> cancelReservationService.cancel(
                        new CancelReservationCommand(
                                otherMember.getId(),
                                applied.reservationId(),
                                time(2027, 3, 19, 21)
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "자신이 신청한 예약만 취소할 수 있습니다."
                );
    }

    @Test
    @DisplayName("이용 시작 정각부터 신청자는 취소할 수 없다")
    void requesterCannotCancelAtStart() {
        User member = createMember(createClub());

        ApplyReservationResult applied = applyStandby(
                member,
                findRoom("321"),
                20,
                10,
                12
        );

        assertThatThrownBy(
                () -> cancelReservationService.cancel(
                        new CancelReservationCommand(
                                member.getId(),
                                applied.reservationId(),
                                time(2027, 3, 20, 10)
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "이용 시작 이후에는 신청자가 취소할 수 없습니다."
                );
    }

    @Test
    @DisplayName("신청 마감 후라도 정규예약 취소 시 시작 전이면 대기예약을 복구한다")
    void restoresStandbyAfterDeadlineBeforeStart() {
        User standbyMember = createMember(createClub());

        Club regularClub = createClub();
        User regularMember = createMember(regularClub);
        saveQuota(regularClub, YearMonth.of(2027, 3), 10 * 60L);

        Room room = findRoom("321");

        ApplyReservationResult standby = applyStandby(
                standbyMember,
                room,
                20,
                10,
                12
        );

        ApplyReservationResult regular = applyRegular(
                regularMember,
                room,
                21,
                10,
                12
        );

        CancelReservationResult result =
                cancelReservationService.cancel(
                        new CancelReservationCommand(
                                regularMember.getId(),
                                regular.reservationId(),
                                time(2027, 3, 20, 9)
                        )
                );

        entityManager.clear();

        Reservation restoredStandby = reservationRepository
                .findById(standby.reservationId())
                .orElseThrow();

        assertThat(restoredStandby.getStatus())
                .isEqualTo(ReservationStatus.PENDING);

        assertThat(result.restoredReservationIds())
                .containsExactly(restoredStandby.getId());
    }

    @Test
    @DisplayName("밀려난 신청자가 다른 방을 예약했다면 자동 복구하지 않는다")
    void doesNotRestoreWhenRequesterBookedOtherRoom() {
        User standbyMember = createMember(createClub());

        Club regularClub = createClub();
        User regularMember = createMember(regularClub);
        saveQuota(regularClub, YearMonth.of(2027, 3), 10 * 60L);

        ApplyReservationResult standby = applyStandby(
                standbyMember,
                findRoom("321"),
                20,
                10,
                12
        );

        ApplyReservationResult regular = applyRegular(
                regularMember,
                findRoom("321"),
                21,
                10,
                12
        );

        applyReservationService.apply(
                new ApplyReservationCommand(
                        standbyMember.getId(),
                        findRoom("322").getId(),
                        ReservationType.STANDBY,
                        time(2027, 3, 20, 10),
                        time(2027, 3, 20, 12),
                        time(2027, 3, 19, 21)
                                .plusMinutes(30)
                )
        );

        CancelReservationResult result =
                cancelReservationService.cancel(
                        new CancelReservationCommand(
                                regularMember.getId(),
                                regular.reservationId(),
                                time(2027, 3, 20, 9)
                        )
                );

        entityManager.clear();

        Reservation displacedStandby = reservationRepository
                .findById(standby.reservationId())
                .orElseThrow();

        assertThat(displacedStandby.getStatus())
                .isEqualTo(ReservationStatus.DISPLACED);

        assertThat(result.restoredReservationIds())
                .isEmpty();
    }

    @Test
    @DisplayName("여러 대기예약은 최초 신청 시각 순서로 복구한다")
    void restoresMultipleStandbysByOriginalRequestTime() {
        User firstStandbyMember = createMember(createClub());
        User secondStandbyMember = createMember(createClub());

        Club regularClub = createClub();
        User regularMember = createMember(regularClub);
        saveQuota(regularClub, YearMonth.of(2027, 3), 10 * 60L);

        Room room = findRoom("321");

        ApplyReservationResult firstStandby =
                applyReservationService.apply(
                        new ApplyReservationCommand(
                                firstStandbyMember.getId(),
                                room.getId(),
                                ReservationType.STANDBY,
                                time(2027, 3, 20, 10),
                                time(2027, 3, 20, 11),
                                time(2027, 3, 19, 19)
                        )
                );

        ApplyReservationResult secondStandby =
                applyReservationService.apply(
                        new ApplyReservationCommand(
                                secondStandbyMember.getId(),
                                room.getId(),
                                ReservationType.STANDBY,
                                time(2027, 3, 20, 11),
                                time(2027, 3, 20, 12),
                                time(2027, 3, 19, 20)
                        )
                );

        ApplyReservationResult regular = applyRegular(
                regularMember,
                room,
                21,
                10,
                12
        );

        CancelReservationResult result =
                cancelReservationService.cancel(
                        new CancelReservationCommand(
                                regularMember.getId(),
                                regular.reservationId(),
                                time(2027, 3, 20, 9)
                        )
                );

        assertThat(result.restoredReservationIds())
                .containsExactly(
                        firstStandby.reservationId(),
                        secondStandby.reservationId()
                );
    }

    @Test
    @DisplayName("신청자가 밀려난 예약을 취소하면 이후 자동 복구하지 않는다")
    void canceledDisplacedReservationIsNotRestored() {
        User standbyMember = createMember(createClub());

        Club regularClub = createClub();
        User regularMember = createMember(regularClub);
        saveQuota(regularClub, YearMonth.of(2027, 3), 10 * 60L);

        Room room = findRoom("321");

        ApplyReservationResult standby = applyStandby(
                standbyMember,
                room,
                20,
                10,
                12
        );

        ApplyReservationResult regular = applyRegular(
                regularMember,
                room,
                21,
                10,
                12
        );

        cancelReservationService.cancel(
                new CancelReservationCommand(
                        standbyMember.getId(),
                        standby.reservationId(),
                        time(2027, 3, 19, 21)
                                .plusMinutes(30)
                )
        );

        CancelReservationResult result =
                cancelReservationService.cancel(
                        new CancelReservationCommand(
                                regularMember.getId(),
                                regular.reservationId(),
                                time(2027, 3, 20, 9)
                        )
                );

        entityManager.clear();

        Reservation canceledStandby = reservationRepository
                .findById(standby.reservationId())
                .orElseThrow();

        assertThat(canceledStandby.getStatus())
                .isEqualTo(ReservationStatus.CANCELED);

        assertThat(result.restoredReservationIds())
                .isEmpty();
    }

    private ApplyReservationResult applyStandby(
            User member,
            Room room,
            int requestedHour,
            int startHour,
            int endHour
    ) {
        return applyReservationService.apply(
                new ApplyReservationCommand(
                        member.getId(),
                        room.getId(),
                        ReservationType.STANDBY,
                        time(2027, 3, 20, startHour),
                        time(2027, 3, 20, endHour),
                        time(2027, 3, 19, requestedHour)
                )
        );
    }

    private ApplyReservationResult applyRegular(
            User member,
            Room room,
            int requestedHour,
            int startHour,
            int endHour
    ) {
        return applyReservationService.apply(
                new ApplyReservationCommand(
                        member.getId(),
                        room.getId(),
                        ReservationType.REGULAR,
                        time(2027, 3, 20, startHour),
                        time(2027, 3, 20, endHour),
                        time(2027, 3, 19, requestedHour)
                )
        );
    }

    private Club createClub() {
        return clubRepository.saveAndFlush(
                new Club(unique("취소서비스테스트동아리"))
        );
    }

    private User createMember(Club club) {
        return userRepository.saveAndFlush(
                User.member(
                        unique("cancel-member"),
                        "temporary-password-hash",
                        "취소 테스트 회원",
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
