package com.cnu.practiceroom.user.repository;

import com.cnu.practiceroom.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    List<User> findAllByClub_IdAndActiveTrueOrderByNameAsc(
            Long clubId
    );
}