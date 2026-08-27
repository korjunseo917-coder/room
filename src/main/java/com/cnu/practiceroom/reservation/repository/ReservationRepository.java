package com.cnu.practiceroom.reservation.repository;

import com.cnu.practiceroom.reservation.domain.Reservation;
import com.cnu.practiceroom.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    List<Reservation>
    findAllByRequester_IdOrderByStartAtDesc(Long requesterId);
}