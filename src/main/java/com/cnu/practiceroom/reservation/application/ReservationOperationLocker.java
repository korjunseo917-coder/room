package com.cnu.practiceroom.reservation.application;

import com.cnu.practiceroom.room.repository.RoomRepository;
import com.cnu.practiceroom.user.domain.User;
import com.cnu.practiceroom.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class ReservationOperationLocker {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    public ReservationOperationLocker(
            UserRepository userRepository,
            RoomRepository roomRepository
    ) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
    }

    public List<User> lockUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "잠글 사용자는 최소 한 명 필요합니다."
            );
        }

        List<Long> sortedIds = new ArrayList<>(
                new LinkedHashSet<>(userIds)
        );
        sortedIds.sort(Comparator.naturalOrder());

        List<User> lockedUsers = userRepository
                .findAllByIdForUpdate(sortedIds);

        if (lockedUsers.size() != sortedIds.size()) {
            throw new IllegalStateException(
                    "예약과 관련된 사용자를 찾을 수 없습니다."
            );
        }

        return List.copyOf(lockedUsers);
    }

    public void lockRoom(long roomId) {
        roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "연습실을 찾을 수 없습니다."
                        )
                );
    }
}
