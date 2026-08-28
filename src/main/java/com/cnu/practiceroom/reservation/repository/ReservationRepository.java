package com.cnu.practiceroom.reservation.repository;

import com.cnu.practiceroom.reservation.domain.Reservation;
import com.cnu.practiceroom.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    @Query("""
            select r
            from Reservation r
            where r.room.id = :roomId
              and r.status in :statuses
              and r.startAt < :endAt
              and r.endAt > :startAt
            order by r.startAt
            """)
    List<Reservation> findOverlapping(
            @Param("roomId") Long roomId,
            @Param("statuses")
            Collection<ReservationStatus> statuses,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query("""
            select r
            from Reservation r
            where r.requester.id = :requesterId
              and r.room.id <> :roomId
              and r.status in :statuses
              and r.startAt < :endAt
              and r.endAt > :startAt
            order by r.startAt
            """)
    List<Reservation> findRequesterOverlappingOtherRooms(
            @Param("requesterId") Long requesterId,
            @Param("roomId") Long roomId,
            @Param("statuses")
            Collection<ReservationStatus> statuses,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query(
            value = """
                    select coalesce(sum(u.usage_minutes), 0)
                    from reservation_monthly_usage u
                    join reservations r
                      on r.id = u.reservation_id
                    where r.club_id = :clubId
                      and u.usage_month = :usageMonth
                      and r.type = 'REGULAR'
                      and r.status in (
                          'PENDING',
                          'APPROVED',
                          'COMPLETED'
                      )
                    """,
            nativeQuery = true
    )
    long sumCountedRegularUsageMinutes(
            @Param("clubId") Long clubId,
            @Param("usageMonth") LocalDate usageMonth
    );

    List<Reservation>
    findAllByRequester_IdOrderByStartAtDesc(Long requesterId);
}
