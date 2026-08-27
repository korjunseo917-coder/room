package com.cnu.practiceroom.reservation.domain;

import com.cnu.practiceroom.club.domain.Club;
import com.cnu.practiceroom.room.domain.Room;
import com.cnu.practiceroom.user.domain.User;
import com.cnu.practiceroom.user.domain.UserRole;
import jakarta.persistence.*;

import java.time.*;
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
            ZonedDateTime end
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



        this.requester = requester;
        this.club = requester.getClub();
        this.room = room;
        this.type = type;
        this.status = ReservationStatus.PENDING;
        this.startAt = start.toInstant();
        this.endAt = end.toInstant();

        Map<YearMonth, Long> calculatedUsage =
                new ReservationPolicy()
                        .calculateUsageMinutesByOperationalMonth(
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
            ZonedDateTime end
    ) {
        return new Reservation(
                requester,
                room,
                type,
                start,
                end
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


    public Map<YearMonth, Long>
    getUsageMinutesByMonth() {
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
