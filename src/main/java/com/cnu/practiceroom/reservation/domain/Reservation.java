package com.cnu.practiceroom.reservation.domain;

import com.cnu.practiceroom.club.domain.Club;
import com.cnu.practiceroom.room.domain.Room;
import com.cnu.practiceroom.user.domain.User;
import com.cnu.practiceroom.user.domain.UserRole;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "reservations")
public class Reservation {

    private static final ZoneId SEOUL =
            ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "usage_month", nullable = false)
    private LocalDate usageMonth;

    @ElementCollection
    @CollectionTable(
            name = "reservation_monthly_usage",
            joinColumns = @JoinColumn(name = "reservation_id")
    )
    @MapKeyColumn(name = "usage_month")
    @Column(name = "usage_minutes", nullable = false)
    private Map<LocalDate, Long> usageMinutesByMonth =
            new LinkedHashMap<>();

    @Column(
            name = "requested_at",
            nullable = false,
            updatable = false
    )
    private Instant requestedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_id")
    private User decidedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "displaced_at")
    private Instant displacedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "displaced_by_id")
    private Reservation displacedBy;

    protected Reservation() {
    }

    private Reservation(
            User requester,
            Room room,
            ReservationType type,
            ZonedDateTime start,
            ZonedDateTime end,
            ReservationPolicy policy
    ) {
        if (requester == null) {
            throw new IllegalArgumentException(
                    "신청자는 필수입니다."
            );
        }

        if (requester.getRole() != UserRole.MEMBER) {
            throw new IllegalArgumentException(
                    "일반 회원만 예약을 신청할 수 있습니다."
            );
        }

        if (requester.getClub() == null) {
            throw new IllegalArgumentException(
                    "신청자는 동아리에 소속되어야 합니다."
            );
        }

        if (room == null) {
            throw new IllegalArgumentException(
                    "연습실은 필수입니다."
            );
        }

        if (type == null) {
            throw new IllegalArgumentException(
                    "예약 종류는 필수입니다."
            );
        }

        if (policy == null) {
            throw new IllegalArgumentException(
                    "예약 정책은 필수입니다."
            );
        }

        /*
         * start와 end를 저장하기 전에 예약 시간 규칙을 검사한다.
         *
         * 검사 내용:
         * - 시작과 종료 시각이 null이 아닌지
         * - 종료가 시작보다 늦은지
         * - 예약 시각이 슬롯 경계에 맞는지
         * - 최소 및 최대 이용시간을 지키는지
         */
        policy.validateTimeRange(start, end);

        this.requester = requester;
        this.club = requester.getClub();
        this.room = room;
        this.type = type;
        this.status = ReservationStatus.PENDING;
        this.startAt = start.toInstant();
        this.endAt = end.toInstant();

        /*
         * 예약 신청 시점에 전달받은 정책으로
         * 운영월별 사용시간을 계산한다.
         *
         * 계산한 결과를 예약 자체에 저장하기 때문에
         * 관리자가 나중에 정책을 변경하더라도
         * 기존 예약의 사용시간은 바뀌지 않는다.
         */
        Map<YearMonth, Long> calculatedUsage =
                policy.calculateUsageMinutesByOperationalMonth(
                        start,
                        end
                );

        calculatedUsage.forEach(
                (month, minutes) ->
                        this.usageMinutesByMonth.put(
                                month.atDay(1),
                                minutes
                        )
        );

        this.usageMonth = calculatedUsage
                .keySet()
                .iterator()
                .next()
                .atDay(1);
    }

    public static Reservation pending(
            User requester,
            Room room,
            ReservationType type,
            ZonedDateTime start,
            ZonedDateTime end,
            ReservationPolicy policy
    ) {
        return new Reservation(
                requester,
                room,
                type,
                start,
                end,
                policy
        );
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();
        this.requestedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        this.updatedAt = Instant.now();
    }

    public ReservationSnapshot toSnapshot() {
        if (id == null) {
            throw new IllegalStateException(
                    "저장되지 않은 예약은 Snapshot으로 변환할 수 없습니다."
            );
        }

        return new ReservationSnapshot(
                id,
                requester.getId(),
                room.getId(),
                type,
                status,
                startAt.atZone(SEOUL),
                endAt.atZone(SEOUL)
        );
    }

    public Long getId() {
        return id;
    }

    public User getRequester() {
        return requester;
    }

    public Club getClub() {
        return club;
    }

    public Room getRoom() {
        return room;
    }

    public ReservationType getType() {
        return type;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public LocalDate getUsageMonth() {
        return usageMonth;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public Instant getDisplacedAt() {
        return displacedAt;
    }

    public Reservation getDisplacedBy() {
        return displacedBy;
    }

    public Map<YearMonth, Long> getUsageMinutesByMonth() {
        Map<YearMonth, Long> result =
                new LinkedHashMap<>();

        usageMinutesByMonth.forEach(
                (month, minutes) ->
                        result.put(
                                YearMonth.from(month),
                                minutes
                        )
        );

        return Map.copyOf(result);
    }
}