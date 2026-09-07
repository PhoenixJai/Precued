package com.precued.service;

import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.User;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRepository;
import com.precued.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal room-join: creates a RoomParticipant directly from a room_id, with
 * no invite-token consumption or role assignment — that's separate scope.
 * userId is nullable for a guest join (Precued_DataModel.md's RoomParticipant.user_id).
 */
@Service
public class RoomParticipantService {

    private final RoomParticipantRepository roomParticipantRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public RoomParticipantService(
            RoomParticipantRepository roomParticipantRepository,
            RoomRepository roomRepository,
            UserRepository userRepository) {
        this.roomParticipantRepository = roomParticipantRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    public RoomParticipant join(UUID roomId, UUID userId, String displayName) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("No Room with id " + roomId));
        User user = userId == null
                ? null
                : userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("No User with id " + userId));

        RoomParticipant participant = new RoomParticipant();
        participant.setRoom(room);
        participant.setUser(user);
        participant.setLivekitIdentity(UUID.randomUUID().toString());
        participant.setDisplayName(displayName);
        participant.setJoinedAt(Instant.now());

        return roomParticipantRepository.save(participant);
    }
}
