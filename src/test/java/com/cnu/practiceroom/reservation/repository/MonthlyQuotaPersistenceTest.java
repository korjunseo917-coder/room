package com.cnu.practiceroom.reservation.repository;

import com.cnu.practiceroom.club.domain.Club;
import com.cnu.practiceroom.club.repository.ClubRepository;
import com.cnu.practiceroom.reservation.domain.ClubMonthlyQuota;
import com.cnu.practiceroom.reservation.domain.Reservation;
import com.cnu.practiceroom.reservation.domain.ReservationPolicy;
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

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure
        .AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = NONE)
class MonthlyQuotaPersistenceTest {

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
    @DisplayName("동아리의 월간 허용시간을 저장한다")
    void savesMonthlyQuota() {
        Club club = createClub();

        quotaRepository.saveAndFlush(
                new ClubMonthlyQuota(
                        club,
                        YearMonth.of(2026, 10),
                        20 * 60L
                )
        );

        entityManager.clear();

        ClubMonthlyQuota found = quotaRepository
                .findByClub_IdAndUsageMonth(
                        club.getId(),
                        YearMonth.of(2026, 10).atDay(1)
                )
                .orElseThrow();

        assertThat(found.getQuotaMinutes())
                .isEqualTo(1_200L);
    }

    @Test
    @DisplayName("07시 경계를 지나는 예약은 두 운영월로 나뉜다")
    void splitsUsageAcrossOperationalMonths() {
        Club club = createClub();

        User member = userRepository.saveAndFlush(
                User.member(
                        unique("member"),
                        "temporary-password-hash",
                        "테스트 회원",
                        club
                )
        );

        Room room = roomRepository
                .findByRoomNumber("321")
                .orElseThrow();

        Reservation saved = reservationRepository.saveAndFlush(
                Reservation.pending(
                        member,
                        room,
                        ReservationType.REGULAR,
                        time(2026, 10, 1, 6),
                        time(2026, 10, 1, 9),
                        time(2026, 9, 29, 21),
                        reservationPolicy
                )
        );

        entityManager.clear();

        Reservation found = reservationRepository
                .findById(saved.getId())
                .orElseThrow();

        assertThat(found.getUsageMinutesByMonth())
                .isEqualTo(
                        Map.of(
                                YearMonth.of(2026, 9), 60L,
                                YearMonth.of(2026, 10), 120L
                        )
                );
    }

    private Club createClub() {
        return clubRepository.saveAndFlush(
                new Club(unique("월간시간테스트동아리"))
        );
    }

    private String unique(String prefix) {
        return prefix + "-"
                + UUID.randomUUID();
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