package com.precued.service;

import com.precued.entity.Invite;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.repository.InviteRepository;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomRoleRepository;
import com.precued.security.CurrentParticipantContext;
import com.precued.util.OpaqueTokenGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class InviteService {

    private final InviteRepository inviteRepository;
    private final RoomRoleRepository roomRoleRepository;
    private final ParticipantRoleAssignmentRepository assignmentRepository;
    private final int defaultTtlDays;

    public InviteService(
            InviteRepository inviteRepository,
            RoomRoleRepository roomRoleRepository,
            ParticipantRoleAssignmentRepository assignmentRepository,
            @Value("${precued.invite.default-ttl-days:7}") int defaultTtlDays) {
        this.inviteRepository = inviteRepository;
        this.roomRoleRepository = roomRoleRepository;
        this.assignmentRepository = assignmentRepository;
        this.defaultTtlDays = defaultTtlDays;
    }

    @Transactional
    public Invite create(
            UUID roomId,
            UUID roomRoleId,
            Invite.Mode mode,
            String inviteeEmail,
            Integer requestedMaxUses,
            Instant requestedExpiresAt) {
        requireHost(roomId);

        RoomRole role = roomRoleRepository.findByIdForUpdate(roomRoleId)
                .orElseThrow(() -> new IllegalArgumentException("No RoomRole with id " + roomRoleId));
        if (!role.getRoom().getId().equals(roomId)) {
            throw new IllegalStateException("RoomRole does not belong to this room");
        }
        if (role.isHostRole()) {
            throw new IllegalStateException("Host roles are joined through the Account Holder session, not an invite");
        }
        if (role.getRoom().getStatus() == Room.Status.ENDED) {
            throw new IllegalStateException("Cannot create invites for an ended room");
        }

        Instant now = Instant.now();
        List<Invite> existing = inviteRepository.findByRoomRoleId(roomRoleId);
        existing.forEach(invite -> refreshStatus(invite, now));

        long activeMembers = assignmentRepository.countByRoomRoleIdAndRevokedAtIsNull(roomRoleId);
        long reservedUses = existing.stream()
                .filter(invite -> invite.getStatus() == Invite.Status.PENDING)
                .mapToLong(invite -> Math.max(0, invite.getMaxUses() - invite.getUsesCount()))
                .sum();

        String normalizedEmail = null;
        int maxUses;
        if (mode == null) {
            throw new IllegalStateException("Invite mode is required");
        }
        if (mode == Invite.Mode.NAMED) {
            normalizedEmail = normalizeEmail(inviteeEmail);
            if (normalizedEmail == null) {
                throw new IllegalStateException("Named invites require an invitee email");
            }
            maxUses = 1;
        } else {
            if (inviteeEmail != null && !inviteeEmail.isBlank()) {
                throw new IllegalStateException("Pool invites cannot be tied to one invitee email");
            }
            if (requestedMaxUses == null) {
                if (role.getMaxMembers() == null) {
                    throw new IllegalStateException("Pool invites for unlimited roles require an explicit max uses value");
                }
                long available = role.getMaxMembers() - activeMembers - reservedUses;
                if (available <= 0) {
                    throw new IllegalStateException("Role has no remaining invitation capacity");
                }
                maxUses = Math.toIntExact(available);
            } else {
                if (requestedMaxUses <= 0) {
                    throw new IllegalStateException("Pool invite max uses must be positive");
                }
                maxUses = requestedMaxUses;
            }
        }

        if (role.getMaxMembers() != null) {
            long available = role.getMaxMembers() - activeMembers - reservedUses;
            if (maxUses > available) {
                throw new IllegalStateException(
                        "Invite exceeds remaining role capacity (" + Math.max(0, available) + " seat(s) available)");
            }
        }

        Instant expiresAt = requestedExpiresAt != null
                ? requestedExpiresAt
                : now.plus(defaultTtlDays, ChronoUnit.DAYS);
        if (!expiresAt.isAfter(now)) {
            throw new IllegalStateException("Invite expiration must be in the future");
        }

        Invite invite = new Invite();
        invite.setRoomRole(role);
        invite.setInviteeEmail(normalizedEmail);
        invite.setToken(OpaqueTokenGenerator.generate());
        invite.setMode(mode);
        invite.setMaxUses(maxUses);
        invite.setUsesCount(0);
        invite.setStatus(Invite.Status.PENDING);
        invite.setCreatedAt(now);
        invite.setExpiresAt(expiresAt);
        return inviteRepository.save(invite);
    }

    @Transactional
    public List<Invite> listForRoom(UUID roomId) {
        requireHost(roomId);
        Instant now = Instant.now();
        List<Invite> invites = inviteRepository.findByRoomId(roomId);
        invites.forEach(invite -> refreshStatus(invite, now));
        return invites;
    }

    @Transactional
    public Invite expire(UUID roomId, UUID inviteId) {
        requireHost(roomId);
        Invite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("No Invite with id " + inviteId));
        if (!invite.getRoomRole().getRoom().getId().equals(roomId)) {
            throw new IllegalStateException("Invite does not belong to this room");
        }
        if (invite.getStatus() == Invite.Status.USED) {
            throw new IllegalStateException("A fully used invite cannot be expired retroactively");
        }
        invite.setStatus(Invite.Status.EXPIRED);
        return inviteRepository.save(invite);
    }

    /** Public token preview used before a guest has a RoomParticipant session. */
    @Transactional(noRollbackFor = InviteUnavailableException.class)
    public Invite resolve(String token) {
        Invite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("No invite with this token"));
        refreshStatus(invite, Instant.now());
        requirePending(invite);
        return invite;
    }

    private void requireHost(UUID roomId) {
        RoomParticipant caller = CurrentParticipantContext.get();
        if (caller.getRoom() == null || !roomId.equals(caller.getRoom().getId())) {
            throw new IllegalStateException("Invite management is scoped to the caller's room");
        }
        ParticipantRoleAssignment assignment = assignmentRepository
                .findByRoomParticipantIdAndRevokedAtIsNull(caller.getId())
                .orElseThrow(() -> new IllegalStateException("Only the room host can manage invites"));
        if (!assignment.getRoomRole().isHostRole()) {
            throw new IllegalStateException("Only the room host can manage invites");
        }
    }

    private void refreshStatus(Invite invite, Instant now) {
        if (invite.getStatus() != Invite.Status.PENDING) return;
        boolean roomEnded = invite.getRoomRole().getRoom().getStatus() == Room.Status.ENDED;
        boolean timeExpired = invite.getExpiresAt() != null && !invite.getExpiresAt().isAfter(now);
        if (roomEnded || timeExpired) {
            invite.setStatus(Invite.Status.EXPIRED);
            inviteRepository.save(invite);
            return;
        }
        if (invite.getUsesCount() >= invite.getMaxUses()) {
            invite.setStatus(Invite.Status.USED);
            inviteRepository.save(invite);
        }
    }

    private void requirePending(Invite invite) {
        if (invite.getStatus() == Invite.Status.EXPIRED) {
            throw new InviteUnavailableException("This invite has expired");
        }
        if (invite.getStatus() == Invite.Status.USED || invite.getUsesCount() >= invite.getMaxUses()) {
            throw new InviteUnavailableException("This invite has already been fully used");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
