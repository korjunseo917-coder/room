package com.cnu.practiceroom.reservation.domain;

import com.cnu.practiceroom.club.domain.Club;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(
        name = "club_monthly_quotas",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_monthly_quota_club_month",
                columnNames = {"club_id", "usage_month"}
        )
)
public class ClubMonthlyQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "usage_month", nullable = false)
    private LocalDate usageMonth;

    @Column(name = "quota_minutes", nullable = false)
    private long quotaMinutes;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClubMonthlyQuota() {
    }

    public ClubMonthlyQuota(
            Club club,
            YearMonth usageMonth,
            long quotaMinutes
    ) {
        if (club == null) {
            throw new IllegalArgumentException(
                    "동아리는 필수입니다."
            );
        }

        if (usageMonth == null) {
            throw new IllegalArgumentException(
                    "운영월은 필수입니다."
            );
        }

        validateQuotaMinutes(quotaMinutes);

        this.club = club;
        this.usageMonth = usageMonth.atDay(1);
        this.quotaMinutes = quotaMinutes;
    }

    public void changeQuotaMinutes(long quotaMinutes) {
        validateQuotaMinutes(quotaMinutes);
        this.quotaMinutes = quotaMinutes;
    }

    private void validateQuotaMinutes(long quotaMinutes) {
        if (quotaMinutes <= 0) {
            throw new IllegalArgumentException(
                    "월간 허용시간은 0보다 커야 합니다."
            );
        }

        if (quotaMinutes % 60 != 0) {
            throw new IllegalArgumentException(
                    "월간 허용시간은 1시간 단위여야 합니다."
            );
        }
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Club getClub() {
        return club;
    }

    public YearMonth getUsageMonth() {
        return YearMonth.from(usageMonth);
    }

    public long getQuotaMinutes() {
        return quotaMinutes;
    }
}
