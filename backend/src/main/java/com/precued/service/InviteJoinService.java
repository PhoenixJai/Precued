package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.Invite;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.repository.InviteRepository;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRoleRepository;
import com.precued.util.OpaqueTokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Guest join path. Possession of a valid Invite token determines the RoomRole;
 * the client no longer self-assigns a role after joining.
 */
@Service
public class InviteJoinService {

    private final InviteRepository inviteRepository;
    private final RoomRoleRepository roomRoleRepository;
    private final RoomParticipantRepository participantRepository;
    private final ParticipantRoleAssignmentRepository assignmentRepository;
    private final VisibilityEngine visibilityEngine;

    public InviteJoinService(
            InviteRepository inviteRepository,
            RoomRoleRepository roomRoleRepository,
            RoomParticipantRepository participantRepository,
            ParticipantRoleAssignmentRepository assignmentRepository,
            VisibilityEngine visibilityEngine) {
        this.inviteRepository = inviteRepository;
        this.roomRoleRepository = roomRoleRepository;
        this.participantRepository = participantRepository;
        this.assignmentRepository = assignmentRepository;
        this.visibilityEngine = visibilityEngine;
    }

    @Transactional
    public RoomParticipant join(UUID requestedRoomId, String inviteToken, String displayName) {
        if (inviteToken == null || inviteToken.isBlank()) {
            throw new IllegalStateException("A valid invite token is required for guest joins");
        }

        Invite invite = inviteRepository.findByTokenForUpdate(inviteToken)
                .orElseThrow(() -> new IllegalArgumentException("No invite with this token"));

        Room inviteRoom = invite.getRoomRole().getRoom();
        if (!inviteRoom.getId().equals(requestedRoomId)) {
            throw new IllegalStateException("Invite does not belong to the requested room");
        }

        Instant now = Instant.now();
        if (invite.getStatus() == Invite.Status.EXPIRED
                || inviteRoom.getStatus() == Room.Status.ENDED
                || (invite.getExpiresAt() != null && !invite.getExpiresAt().isAfter(now))) {
            invite.setStatus(Invite.Status.EXPIRED);
            inviteRepository.save(invite);
            throw new IllegalStateException("This invite has expired");
        }
        if (invite.getStatus() == Invite.Status.USED || invite.getUsesCount() >= invite.getMaxUses()) {
            invite.setStatus(Invite.Status.USED);
            inviteRepository.save(invite);
            throw new IllegalStateException("This invite has already been fully used");
        }

        // Serialize every join for this role, even when two different Invite
        // tokens are used at the same instant, so maxMembers cannot be raced.
        RoomRole role = roomRoleRepository.findByIdForUpdate(invite.getRoomRole().getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No RoomRole with id " + invite.getRoomRole().getId()));
        if (!role.getRoom().getId().equals(requestedRoomId)) {
            throw new IllegalStateException("Invite role does not belong to the requested room");
        }
        if (role.isHostRole()) {
            throw new IllegalStateException("Guest invite cannot assign the host role");
        }

        long activeMembers = assignmentRepository.countByRoomRoleIdAndRevokedAtIsNull(role.getId());
        if (role.getMaxMembers() != null && activeMembers >= role.getMaxMembers()) {
            throw new IllegalStateException("Role is already at capacity");
        }

        String trimmedName = displayName == null ? "" : displayName.trim();
        if (trimmedName.isBlank()) {
            throw new IllegalStateException("Display name is required");
        }

        RoomParticipant participant = new RoomParticipant();
        participant.setRoom(role.getRoom());
        participant.setUser(null);
        participant.setLivekitIdentity(UUID.randomUUID().toString());
        participant.setDisplayName(trimmedName);
        participant.setJoinedAt(now);
        participant.setSessionToken(OpaqueTokenGenerator.generate());
        RoomParticipant savedParticipant = participantRepository.save(participant);

        ParticipantRoleAssignment assignment = new ParticipantRoleAssignment();
        assignment.setRoomParticipant(savedParticipant);
        assignment.setRoomRole(role);
        assignment.setInvite(invite);
        assignment.setAssignedAt(now);
        assignmentRepository.save(assignment);

        int nextUses = invite.getUsesCount() + 1;
        invite.setUsesCount(nextUses);
        if (nextUses >= invite.getMaxUses()) {
            invite.setStatus(Invite.Status.USED);
        }
        inviteRepository.save(invite);

        visibilityEngine.recomputeAndPushForRoom(role.getRoom().getId());
        return savedParticipant;
    }
}
