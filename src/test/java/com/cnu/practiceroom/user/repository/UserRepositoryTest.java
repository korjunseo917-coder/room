package com.cnu.practiceroom.user.repository;

import com.cnu.practiceroom.club.domain.Club;
import com.cnu.practiceroom.club.repository.ClubRepository;
import com.cnu.practiceroom.user.domain.User;
import com.cnu.practiceroom.user.domain.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure
        .AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("일반 회원을 동아리와 함께 저장하고 조회한다")
    void savesMemberWithClub() {
        String uniqueValue = UUID.randomUUID().toString();

        Club club = clubRepository.saveAndFlush(
                new Club("테스트동아리-" + uniqueValue)
        );

        User saved = userRepository.saveAndFlush(
                User.member(
                        "member-" + uniqueValue,
                        "temporary-password-hash",
                        "테스트 회원",
                        club
                )
        );

        entityManager.clear();

        User found = userRepository
                .findByLoginId(saved.getLoginId())
                .orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getName()).isEqualTo("테스트 회원");
        assertThat(found.getRole())
                .isEqualTo(UserRole.MEMBER);
        assertThat(found.getClub().getId())
                .isEqualTo(club.getId());
        assertThat(found.isActive()).isTrue();
    }

    @Test
    @DisplayName("관리자는 소속 동아리 없이 저장할 수 있다")
    void savesAdminWithoutClub() {
        String uniqueValue = UUID.randomUUID().toString();

        User saved = userRepository.saveAndFlush(
                User.admin(
                        "admin-" + uniqueValue,
                        "temporary-password-hash",
                        "예약 승인 관리자"
                )
        );

        entityManager.clear();

        User found = userRepository
                .findByLoginId(saved.getLoginId())
                .orElseThrow();

        assertThat(found.getRole())
                .isEqualTo(UserRole.ADMIN);
        assertThat(found.getClub()).isNull();
        assertThat(found.isActive()).isTrue();
    }
}