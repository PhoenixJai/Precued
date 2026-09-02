package com.precued.repository;

import com.precued.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * All repository interfaces in one file for scaffold brevity.
 * Split into individual files as each grows real query methods.
 */

interface TemplateRepository extends JpaRepository<Template, String> {}

interface TemplateRoleRepository extends JpaRepository<TemplateRole, UUID> {
    List<TemplateRole> findByTemplateIdOrderBySortOrder(String templateId);
}

interface TemplatePresetRepository extends JpaRepository<TemplatePreset, UUID> {
    List<TemplatePreset> findByTemplateIdOrderBySortOrder(String templateId);
}

interface TemplatePresetRoleRepository
        extends JpaRepository<TemplatePresetRole, TemplatePresetRole.Id> {
    List<TemplatePresetRole> findByPresetId(UUID presetId);
}

interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}

interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByLivekitRoomName(String livekitRoomName);
}

interface RoomRoleRepository extends JpaRepository<RoomRole, UUID> {
    List<RoomRole> findByRoomId(UUID roomId);
}

interface InviteRepository extends JpaRepository<Invite, UUID> {
    Optional<Invite> findByToken(String token);
    List<Invite> findByRoomRoleId(UUID roomRoleId);
}

interface RoomParticipantRepository extends JpaRepository<RoomParticipant, UUID> {
    List<RoomParticipant> findByRoomId(UUID roomId);
    Optional<RoomParticipant> findByRoomIdAndLivekitIdentity(UUID roomId, String livekitIdentity);
}

interface ParticipantRoleAssignmentRepository
        extends JpaRepository<ParticipantRoleAssignment, UUID> {
    /** Relies on the partial unique index — at most one row will ever match. */
    Optional<ParticipantRoleAssignment> findByRoomParticipantIdAndRevokedAtIsNull(UUID participantId);

    List<ParticipantRoleAssignment> findByRoomParticipantIdOrderByAssignedAtDesc(UUID participantId);
}

interface ShareRepository extends JpaRepository<Share, UUID> {
    List<Share> findByRoomIdAndStatus(UUID roomId, Share.Status status);
    List<Share> findByPublisherIdAndStatus(UUID publisherId, Share.Status status);
}

interface ShareTrackRepository extends JpaRepository<ShareTrack, UUID> {
    List<ShareTrack> findByShareId(UUID shareId);
}

interface ShareRoleGrantRepository extends JpaRepository<ShareRoleGrant, UUID> {
    List<ShareRoleGrant> findByShareIdAndRevokedAtIsNull(UUID shareId);
    Optional<ShareRoleGrant> findByShareIdAndRoomRoleIdAndRevokedAtIsNull(UUID shareId, UUID roomRoleId);
}
