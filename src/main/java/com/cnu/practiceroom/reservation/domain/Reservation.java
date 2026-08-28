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

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status_before_displacement",
            length = 20
    )
    private ReservationStatus statusBeforeDisplacement;

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
            ZonedDateTime requestedAt,
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

        if (!requester.isActive()) {
            throw new IllegalArgumentException(
                    "비활성화된 사용자는 예약을 신청할 수 없습니다."
            );
        }

        if (room == null) {
            throw new IllegalArgumentException(
                    "연습실은 필수입니다."
            );
        }

        if (!room.isActive()) {
            throw new IllegalArgumentException(
                    "비활성화된 연습실에는 예약을 신청할 수 없습니다."
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
         * 예약 시간 범위와 신청 가능 기간을 함께 검사한다.
         *
         * requestedAt은 신청 버튼을 누른 시각이고,
         * start와 end는 실제 연습실 이용 시간이다.
         */
        policy.validateApplicationTime(
                requestedAt,
                start,
                end
        );

        this.requester = requester;
        this.club = requester.getClub();
        this.room = room;
        this.type = type;
        this.status = ReservationStatus.PENDING;
        this.startAt = start.toInstant();
        this.endAt = end.toInstant();
        this.requestedAt = requestedAt
                .withZoneSameInstant(SEOUL)
                .toInstant();

        /*
         * 신청 당시 정책으로 운영월별 사용시간을 계산하고 저장한다.
         * 이후 관리자가 정책을 바꿔도 이 예약의 계산 결과는 유지된다.
         */
        Map<YearMonth, Long> calculatedUsage =
                policy.calculateUsageMinutesByOperationalMonth(
                        start,
                        end
                );

        if (type == ReservationType.REGULAR) {
            calculatedUsage.forEach(
                    (month, minutes) ->
                            this.usageMinutesByMonth.put(
                                    month.atDay(1),
                                    minutes
                            )
            );
        }

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
            ZonedDateTime requestedAt,
            ReservationPolicy policy
    ) {
        return new Reservation(
                requester,
                room,
                type,
                start,
                end,
                requestedAt,
                policy
        );
    }

    public void cancelByRequester(
            ZonedDateTime canceledAt,
            ReservationStatePolicy statePolicy
    ) {
        if (statePolicy == null) {
            throw new IllegalArgumentException(
                    "예약 상태 정책은 필수입니다."
            );
        }

        /*
         * 상태 정책이 다음 사항을 검사한다.
         *
         * - 현재 상태가 PENDING, APPROVED, DISPLACED 중 하나인지
         * - 취소 시각이 이용 시작 시각보다 이른지
         */
        ReservationStatus nextStatus =
                statePolicy.cancelByRequester(
                        this.status,
                        canceledAt,
                        this.startAt.atZone(SEOUL)
                );

        /*
         * 모든 검증이 성공한 뒤에만 상태와 취소 시각을 변경한다.
         * 검증이 실패하면 기존 상태가 그대로 유지된다.
         */
        this.status = nextStatus;
        this.canceledAt = canceledAt
                .withZoneSameInstant(SEOUL)
                .toInstant();
    }

    public void displace(
            ZonedDateTime displacedAt,
            ReservationStatePolicy statePolicy
    ) {
        if (statePolicy == null) {
            throw new IllegalArgumentException(
                    "예약 상태 정책은 필수입니다."
            );
        }

        ReservationStatus previousStatus = this.status;

        ReservationStatus nextStatus =
                statePolicy.displaceStandby(
                        this.type,
                        this.status
                );

        if (displacedAt == null) {
            throw new IllegalArgumentException(
                    "밀려난 시각은 필수입니다."
            );
        }

        this.statusBeforeDisplacement = previousStatus;
        this.status = nextStatus;
        this.displacedAt = displacedAt
                .withZoneSameInstant(SEOUL)
                .toInstant();
        this.displacedBy = null;
    }

    public void recordDisplacedBy(
            Reservation regularReservation
    ) {
        if (this.status != ReservationStatus.DISPLACED) {
            throw new IllegalStateException(
                    "밀려난 예약에만 원인 예약을 기록할 수 있습니다."
            );
        }

        if (regularReservation == null
                || regularReservation.getType()
                != ReservationType.REGULAR) {
            throw new IllegalArgumentException(
                    "자신을 밀어낸 정규예약은 필수입니다."
            );
        }

        if (regularReservation.getId() == null) {
            throw new IllegalStateException(
                    "저장된 정규예약만 밀어낸 예약으로 기록할 수 있습니다."
            );
        }

        this.displacedBy = regularReservation;
    }

    @PrePersist
    private void beforeInsert() {
        this.updatedAt = Instant.now();
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

    public ReservationStatus getStatusBeforeDisplacement() {
        return statusBeforeDisplacement;
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
