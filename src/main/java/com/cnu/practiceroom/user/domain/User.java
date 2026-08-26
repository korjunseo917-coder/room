package com.cnu.practiceroom.user.domain;

import com.cnu.practiceroom.club.domain.Club;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @Column(
            name = "login_id",
            nullable = false,
            unique = true,
            length = 50
    )
    private String loginId;

    @Column(
            name = "password_hash",
            nullable = false,
            length = 255
    )
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean active;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    private User(
            String loginId,
            String passwordHash,
            String name,
            UserRole role,
            Club club
    ) {
        validateText(loginId, "로그인 아이디");
        validateText(passwordHash, "비밀번호 해시");
        validateText(name, "사용자 이름");

        if (role == null) {
            throw new IllegalArgumentException(
                    "사용자 역할은 필수입니다."
            );
        }

        if (role == UserRole.MEMBER && club == null) {
            throw new IllegalArgumentException(
                    "일반 회원은 동아리에 소속되어야 합니다."
            );
        }

        this.loginId = loginId.trim();
        this.passwordHash = passwordHash;
        this.name = name.trim();
        this.role = role;
        this.club = club;
        this.active = true;
    }

    public static User member(
            String loginId,
            String passwordHash,
            String name,
            Club club
    ) {
        return new User(
                loginId,
                passwordHash,
                name,
                UserRole.MEMBER,
                club
        );
    }

    public static User admin(
            String loginId,
            String passwordHash,
            String name
    ) {
        return new User(
                loginId,
                passwordHash,
                name,
                UserRole.ADMIN,
                null
        );
    }

    private static void validateText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "은(는) 비어 있을 수 없습니다."
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

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public Club getClub() {
        return club;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}