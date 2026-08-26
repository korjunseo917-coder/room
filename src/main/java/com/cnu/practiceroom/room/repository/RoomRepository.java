package com.cnu.practiceroom.room.repository;

import com.cnu.practiceroom.room.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository
        extends JpaRepository<Room, Long> {

    Optional<Room> findByRoomNumber(String roomNumber);

    List<Room> findAllByActiveTrueOrderByRoomNumber();
}