package com.cnu.practiceroom.room.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "room_number",
            nullable = false,
            unique = true,
            length = 20
    )
    private String roomNumber;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Room() {
    }

    public Room(String roomNumber, String name) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "연습실 번호는 비어 있을 수 없습니다."
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "연습실 이름은 비어 있을 수 없습니다."
            );
        }

        this.roomNumber = roomNumber.trim();
        this.name = name.trim();
        this.active = true;
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

    public String getRoomNumber() {
        return roomNumber;
    }

    public String getName() {
        return name;
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
