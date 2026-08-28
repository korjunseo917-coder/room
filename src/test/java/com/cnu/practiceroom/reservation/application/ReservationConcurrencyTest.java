package com.cnu.practiceroom.reservation.application;

import com.cnu.practiceroom.club.domain.Club;
import com.cnu.practiceroom.club.repository.ClubRepository;
import com.cnu.practiceroom.reservation.domain.ClubMonthlyQuota;
import com.cnu.practiceroom.reservation.domain.ReservationStatus;
import com.cnu.practiceroom.reservation.domain.ReservationType;
import com.cnu.practiceroom.reservation.repository.ClubMonthlyQuotaRepository;
import com.cnu.practiceroom.room.domain.Room;
import com.cnu.practiceroom.room.repository.RoomRepository;
import com.cnu.practiceroom.user.domain.User;
import com.cnu.practiceroom.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReservationConcurrencyTest {

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
    private ClubMonthlyQuotaRepository quotaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TEST_CLUB_PREFIX =
            "CONCURRENCY_TEST_CLUB";

    @BeforeEach
    void cleanUpAbandonedTestData() {
        cleanUpTestData();
    }

    @AfterEach
    void cleanUpCreatedData() {
        cleanUpTestData();
    }

    @Test
    @DisplayName("같은 연습실과 시간에 동시에 신청하면 한 건만 접수된다")
    void onlyOneConcurrentRequestOccupiesRoomSlot()
            throws Exception {

        User firstMember = createMember(createClub());
        User secondMember = createMember(createClub());
        Room room = findRoom("321");

        ApplyReservationCommand firstCommand =
                standbyCommand(
                        firstMember.getId(),
                        room.getId()
                );

        ApplyReservationCommand secondCommand =
                standbyCommand(
                        secondMember.getId(),
                        room.getId()
                );

        List<ConcurrentApplyOutcome> outcomes =
                runConcurrently(
                        firstCommand,
                        secondCommand
                );

        assertOneSuccessAndOneFailure(outcomes);
    }

    @Test
    @DisplayName("월간 잔여시간이 1시간일 때 동시 정규예약은 한 건만 접수된다")
    void concurrentRegularRequestsDoNotExceedQuota()
            throws Exception {

        Club club = createClub();
        User firstMember = createMember(club);
        User secondMember = createMember(club);

        quotaRepository.saveAndFlush(
                new ClubMonthlyQuota(
                        club,
                        YearMonth.of(2099, 4),
                        60L
                )
        );

        ApplyReservationCommand firstCommand =
                regularCommand(
                        firstMember.getId(),
                        findRoom("321").getId()
                );

        ApplyReservationCommand secondCommand =
                regularCommand(
                        secondMember.getId(),
                        findRoom("322").getId()
                );

        List<ConcurrentApplyOutcome> outcomes =
                runConcurrently(
                        firstCommand,
                        secondCommand
                );

        assertOneSuccessAndOneFailure(outcomes);
    }

    private List<ConcurrentApplyOutcome> runConcurrently(
            ApplyReservationCommand firstCommand,
            ApplyReservationCommand secondCommand
    ) throws Exception {
        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ConcurrentApplyOutcome> first =
                    executor.submit(
                            () -> applyAfterSignal(
                                    firstCommand,
                                    ready,
                                    start
                            )
                    );

            Future<ConcurrentApplyOutcome> second =
                    executor.submit(
                            () -> applyAfterSignal(
                                    secondCommand,
                                    ready,
                                    start
                            )
                    );

            assertThat(
                    ready.await(5, TimeUnit.SECONDS)
            ).isTrue();

            start.countDown();

            return List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private ConcurrentApplyOutcome applyAfterSignal(
            ApplyReservationCommand command,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();

        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return ConcurrentApplyOutcome.failure(
                        new IllegalStateException(
                                "동시 실행 신호를 기다리다 실패했습니다."
                        )
                );
            }

            ApplyReservationResult result =
                    applyReservationService.apply(command);

            return ConcurrentApplyOutcome.success(result);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ConcurrentApplyOutcome.failure(exception);
        } catch (RuntimeException exception) {
            return ConcurrentApplyOutcome.failure(exception);
        }
    }

    private void assertOneSuccessAndOneFailure(
            List<ConcurrentApplyOutcome> outcomes
    ) {
        assertThat(outcomes)
                .filteredOn(ConcurrentApplyOutcome::succeeded)
                .singleElement()
                .extracting(
                        outcome -> outcome.result().status()
                )
                .isEqualTo(ReservationStatus.PENDING);

        assertThat(outcomes)
                .filteredOn(
                        outcome -> !outcome.succeeded()
                )
                .singleElement()
                .extracting(ConcurrentApplyOutcome::failure)
                .isInstanceOf(RuntimeException.class);
    }

    private ApplyReservationCommand standbyCommand(
            long requesterId,
            long roomId
    ) {
        return new ApplyReservationCommand(
                requesterId,
                roomId,
                ReservationType.STANDBY,
                time(2099, 4, 10, 10),
                time(2099, 4, 10, 11),
                time(2099, 4, 9, 20)
        );
    }

    private ApplyReservationCommand regularCommand(
            long requesterId,
            long roomId
    ) {
        return new ApplyReservationCommand(
                requesterId,
                roomId,
                ReservationType.REGULAR,
                time(2099, 4, 10, 10),
                time(2099, 4, 10, 11),
                time(2099, 4, 9, 20)
        );
    }

    private Club createClub() {
        return clubRepository.saveAndFlush(
                new Club(unique(TEST_CLUB_PREFIX))
        );
    }

    private void cleanUpTestData() {
        String testClubIds = """
                select id
                from clubs
                where starts_with(name, ?)
                """;

        jdbcTemplate.update(
                "delete from reservations where club_id in ("
                        + testClubIds + ")",
                TEST_CLUB_PREFIX
        );
        jdbcTemplate.update(
                "delete from club_monthly_quotas where club_id in ("
                        + testClubIds + ")",
                TEST_CLUB_PREFIX
        );
        jdbcTemplate.update(
                "delete from users where club_id in ("
                        + testClubIds + ")",
                TEST_CLUB_PREFIX
        );
        jdbcTemplate.update(
                "delete from clubs where starts_with(name, ?)",
                TEST_CLUB_PREFIX
        );
    }

    private User createMember(Club club) {
        return userRepository.saveAndFlush(
                User.member(
                        unique("concurrent-member"),
                        "temporary-password-hash",
                        "동시성 테스트 회원",
                        club
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

    private record ConcurrentApplyOutcome(
            ApplyReservationResult result,
            Throwable failure
    ) {
        private static ConcurrentApplyOutcome success(
                ApplyReservationResult result
        ) {
            return new ConcurrentApplyOutcome(result, null);
        }

        private static ConcurrentApplyOutcome failure(
                Throwable failure
        ) {
            return new ConcurrentApplyOutcome(null, failure);
        }

        private boolean succeeded() {
            return result != null;
        }
    }
}
