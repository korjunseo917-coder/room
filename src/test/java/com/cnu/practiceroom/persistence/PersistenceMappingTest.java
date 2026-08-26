package com.cnu.practiceroom.persistence;

import com.cnu.practiceroom.club.domain.Club;
import com.cnu.practiceroom.club.repository.ClubRepository;
import com.cnu.practiceroom.room.domain.Room;
import com.cnu.practiceroom.room.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure
        .AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = NONE)
class PersistenceMappingTest {

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    @DisplayName("Flyway가 등록한 321호와 322호를 조회한다")
    void findsInitialRooms() {
        List<Room> rooms =
                roomRepository.findAllByActiveTrueOrderByRoomNumber();

        assertThat(rooms)
                .extracting(Room::getRoomNumber)
                .containsExactly("321", "322");
    }

    @Test
    @DisplayName("동아리를 PostgreSQL에 저장하고 이름으로 조회한다")
    void savesAndFindsClub() {
        String clubName =
                "테스트동아리-" + UUID.randomUUID();

        Club saved =
                clubRepository.saveAndFlush(new Club(clubName));

        Club found = clubRepository.findByName(clubName)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo(clubName);
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
