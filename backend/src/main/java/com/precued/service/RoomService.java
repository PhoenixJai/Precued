package com.precued.service;

import com.precued.entity.Room;
import com.precued.entity.RoomRole;
import com.precued.entity.Template;
import com.precued.entity.TemplateRole;
import com.precued.entity.User;
import com.precued.repository.RoomRepository;
import com.precued.repository.RoomRoleRepository;
import com.precued.repository.TemplateRepository;
import com.precued.repository.TemplateRoleRepository;
import com.precued.repository.UserRepository;
import com.precued.security.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomRoleRepository roomRoleRepository;
    private final TemplateRepository templateRepository;
    private final TemplateRoleRepository templateRoleRepository;
    private final UserRepository userRepository;

    public RoomService(
            RoomRepository roomRepository,
            RoomRoleRepository roomRoleRepository,
            TemplateRepository templateRepository,
            TemplateRoleRepository templateRoleRepository,
            UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.roomRoleRepository = roomRoleRepository;
        this.templateRepository = templateRepository;
        this.templateRoleRepository = templateRoleRepository;
        this.userRepository = userRepository;
    }

    /**
     * hostDisconnectPolicy defaults to END_CALL (Precued_DataModel.md) when
     * null. Also copies every TemplateRole under the template into a
     * RoomRole row (Decision #1: "Roles are copied from Template into Room
     * at creation, not referenced live") — without this, a room has zero
     * RoomRoles and no host role can ever be assigned to it.
     *
     * The creator is CurrentUserContext.get() — the User resolved from the
     * caller's AuthSession bearer token by AuthSessionInterceptor — never a
     * request-body field. A body-supplied createdByUserId was the original
     * vulnerability: any caller could claim to be any existing User.
     * CurrentUserContext's User comes from a request already closed (the
     * interceptor's own repository call), so it's re-fetched here inside
     * this method's own transaction rather than reused directly, matching
     * how this codebase always re-fetches across a transaction boundary
     * instead of trusting a possibly-detached entity's non-ID fields.
     */
    @Transactional
    public Room create(String templateId, Room.HostDisconnectPolicy hostDisconnectPolicy) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("No Template with id " + templateId));
        UUID createdByUserId = CurrentUserContext.get().getId();
        User createdBy = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated User " + createdByUserId + " no longer exists"));

        Room room = new Room();
        room.setTemplate(template);
        room.setCreatedBy(createdBy);
        room.setLivekitRoomName("room-" + UUID.randomUUID());
        room.setStatus(Room.Status.CREATED);
        room.setHostDisconnectPolicy(
                hostDisconnectPolicy == null ? Room.HostDisconnectPolicy.END_CALL : hostDisconnectPolicy);
        room.setCreatedAt(Instant.now());

        Room saved = roomRepository.save(room);

        List<RoomRole> roomRoles = templateRoleRepository.findByTemplateIdOrderBySortOrder(templateId).stream()
                .map(templateRole -> toRoomRole(saved, templateRole))
                .toList();
        roomRoleRepository.saveAll(roomRoles);

        return saved;
    }

    private RoomRole toRoomRole(Room room, TemplateRole templateRole) {
        RoomRole roomRole = new RoomRole();
        roomRole.setRoom(room);
        roomRole.setSourceTemplateRoleId(templateRole.getId());
        roomRole.setRoleKey(templateRole.getRoleKey());
        roomRole.setName(templateRole.getName());
        roomRole.setHostRole(templateRole.isHostRole());
        roomRole.setMaxMembers(templateRole.getMaxMembers());
        return roomRole;
    }

    public Room get(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("No Room with id " + roomId));
    }

    public List<RoomRole> listRoles(UUID roomId) {
        get(roomId); // 404 if the room itself doesn't exist
        return roomRoleRepository.findByRoomId(roomId);
    }
}
