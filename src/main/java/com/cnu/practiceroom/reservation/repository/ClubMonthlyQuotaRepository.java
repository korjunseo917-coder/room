package com.cnu.practiceroom.reservation.repository;

import com.cnu.practiceroom.reservation.domain.ClubMonthlyQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ClubMonthlyQuotaRepository
        extends JpaRepository<ClubMonthlyQuota, Long> {

    Optional<ClubMonthlyQuota>
    findByClub_IdAndUsageMonth(
            Long clubId,
            LocalDate usageMonth
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select q
            from ClubMonthlyQuota q
            where q.club.id = :clubId
              and q.usageMonth = :usageMonth
            """)
    Optional<ClubMonthlyQuota> findForUpdate(
            @Param("clubId") Long clubId,
            @Param("usageMonth") LocalDate usageMonth
    );
}
