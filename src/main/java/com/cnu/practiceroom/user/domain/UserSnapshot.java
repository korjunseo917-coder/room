package com.cnu.practiceroom.user.domain;

import java.util.Set;

public record UserSnapshot(
        long id,
        Long clubId,
        Set<UserRole> roles,
        boolean activeMembership
) {

    public UserSnapshot {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "사용자 ID는 0보다 커야 합니다."
            );
        }

        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException(
                    "사용자 역할은 최소 하나 이상 필요합니다."
            );
        }

        roles = Set.copyOf(roles);

        if (roles.contains(UserRole.MEMBER)
                && clubId == null) {

            throw new IllegalArgumentException(
                    "동아리 부원은 소속 동아리가 필요합니다."
            );
        }

        if (clubId != null && clubId <= 0) {
            throw new IllegalArgumentException(
                    "동아리 ID는 0보다 커야 합니다."
            );
        }
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }
}