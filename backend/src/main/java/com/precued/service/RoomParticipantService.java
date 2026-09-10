package com.precued.service;

import com.precued.controller.dto.RoomParticipantWithGrantsResponse;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.entity.User;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRepository;
import com.precued.repository.ShareRoleGrantRepository;
import com.precued.repository.UserRepository;
import com.precued.security.AuthenticationRequiredException;
import com.precued.security.CurrentUserContext;
import com.precued.util.OpaqueTokenGenerator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal room-join: creates a RoomParticipant directly from a room_id, with
 * no invite-token consumption or role assignment — that's separate scope.
 * userId is nullable for a guest join (Precued_DataModel.md's
 * RoomParticipant.user_id); a non-null userId must match the caller's
 * AuthSession (see #join) — never trusted as a bare claim.
 */
@Service
public class RoomParticipantService {

    private final RoomParticipantRepository roomParticipantRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final ParticipantRoleAssignmentRepository assignmentRepository;
    private final ShareRoleGrantRepository shareRoleGrantRepository;

    public RoomParticipantService(
            RoomParticipantRepository roomParticipantRepository,
            RoomRepository roomRepository,
            UserRepository userRepository,
            ParticipantRoleAssignmentRepository assignmentRepository,
            ShareRoleGrantRepository shareRoleGrantRepository) {
        this.roomParticipantRepository = roomParticipantRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.shareRoleGrantRepository = shareRoleGrantRepository;
    }

    public RoomParticipant join(UUID roomId, UUID userId, String displayName) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("No Room with id " + roomId));

        User user;
        if (userId == null) {
            user = null;
        } else {
            // A non-null userId is a claim of User identity — AuthSessionInterceptor
            // resolves the bearer token if one was sent, but can't require it
            // itself (a guest join legitimately sends none), so this is the
            // one place that decides whether the claim needed proof at all.
            User authenticatedUser = CurrentUserContext.getIfPresent()
                    .orElseThrow(() -> new AuthenticationRequiredException(
                            "Joining as User " + userId + " requires a valid AuthSession bearer token"));
            if (!authenticatedUser.getId().equals(userId)) {
                throw new IllegalStateException(
                        "Authenticated session does not match the claimed userId " + userId);
            }
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("No User with id " + userId));
        }

        RoomParticipant participant = new RoomParticipant();
        participant.setRoom(room);
        participant.setUser(user);
        participant.setLivekitIdentity(UUID.randomUUID().toString());
        participant.setDisplayName(displayName);
        participant.setJoinedAt(Instant.now());
        participant.setSessionToken(OpaqueTokenGenerator.generate());

        return roomParticipantRepository.save(participant);
    }

    /**
     * Every RoomParticipant in a room, each with their currently active
     * RoomRole (null in the gap between a revoke and the next assign) and
     * every ShareRoleGrant currently applicable to that role — i.e. not
     * revoked and on a Share that is still ACTIVE.
     */
    public List<RoomParticipantWithGrantsResponse> listWithGrants(UUID roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new IllegalArgumentException("No Room with id " + roomId);
        }

        return roomParticipantRepository.findByRoomId(roomId).stream()
                .map(this::toResponseWithGrants)
                .toList();
    }

    private RoomParticipantWithGrantsResponse toResponseWithGrants(RoomParticipant participant) {
        Optional<ParticipantRoleAssignment> activeAssignment =
                assignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(participant.getId());

        if (activeAssignment.isEmpty()) {
            return RoomParticipantWithGrantsResponse.from(participant, null, List.of());
        }

        UUID activeRoomRoleId = activeAssignment.get().getRoomRole().getId();
        List<UUID> grantIds = shareRoleGrantRepository
                .findByRoomRoleIdAndRevokedAtIsNullAndShare_Status(activeRoomRoleId, Share.Status.ACTIVE)
                .stream()
                .map(ShareRoleGrant::getId)
                .toList();

        return RoomParticipantWithGrantsResponse.from(participant, activeRoomRoleId, grantIds);
    }
}
