package com.precued.service;

import com.precued.entity.Room;
import com.precued.entity.RoomRole;
import com.precued.entity.RoomStage;
import com.precued.entity.RoomStageRole;
import com.precued.entity.Template;
import com.precued.entity.TemplateRole;
import com.precued.entity.TemplateStage;
import com.precued.entity.TemplateStageRole;
import com.precued.entity.User;
import com.precued.repository.RoomRepository;
import com.precued.repository.RoomRoleRepository;
import com.precued.repository.RoomStageRepository;
import com.precued.repository.RoomStageRoleRepository;
import com.precued.repository.TemplateRepository;
import com.precued.repository.TemplateRoleRepository;
import com.precued.repository.TemplateStageRepository;
import com.precued.repository.TemplateStageRoleRepository;
import com.precued.repository.UserRepository;
import com.precued.security.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomRoleRepository roomRoleRepository;
    private final RoomStageRepository roomStageRepository;
    private final RoomStageRoleRepository roomStageRoleRepository;
    private final TemplateRepository templateRepository;
    private final TemplateRoleRepository templateRoleRepository;
    private final TemplateStageRepository templateStageRepository;
    private final TemplateStageRoleRepository templateStageRoleRepository;
    private final UserRepository userRepository;

    public RoomService(
            RoomRepository roomRepository,
            RoomRoleRepository roomRoleRepository,
            RoomStageRepository roomStageRepository,
            RoomStageRoleRepository roomStageRoleRepository,
            TemplateRepository templateRepository,
            TemplateRoleRepository templateRoleRepository,
            TemplateStageRepository templateStageRepository,
            TemplateStageRoleRepository templateStageRoleRepository,
            UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.roomRoleRepository = roomRoleRepository;
        this.roomStageRepository = roomStageRepository;
        this.roomStageRoleRepository = roomStageRoleRepository;
        this.templateRepository = templateRepository;
        this.templateRoleRepository = templateRoleRepository;
        this.templateStageRepository = templateStageRepository;
        this.templateStageRoleRepository = templateStageRoleRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates the runtime snapshot for a Template. TemplateRoles become
     * RoomRoles, and configured TemplateStages/TemplateStageRoles become
     * RoomStages/RoomStageRoles. All RoomStages begin PENDING; starting and
     * advancing the flow is deliberately a separate runtime-state-machine
     * concern.
     *
     * Session Flow configuration is copied even when the Template's flow is
     * disabled. The enabled flag is snapshotted separately onto Room, so a
     * disabled Room can retain the saved stage configuration without using
     * it. Later Template edits therefore cannot mutate an existing Room.
     *
     * Built-in Templates (createdBy == null) remain launchable by any
     * authenticated account holder. Custom Templates are private-by-default
     * and may only be launched by their creator.
     */
    @Transactional
    public Room create(String templateId, Room.HostDisconnectPolicy hostDisconnectPolicy) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("No Template with id " + templateId));
        UUID createdByUserId = CurrentUserContext.get().getId();
        User createdBy = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated User " + createdByUserId + " no longer exists"));

        if (template.getCreatedBy() != null
                && !template.getCreatedBy().getId().equals(createdBy.getId())) {
            throw new IllegalArgumentException("No Template with id " + templateId);
        }

        Room room = new Room();
        room.setTemplate(template);
        room.setCreatedBy(createdBy);
        room.setLivekitRoomName("room-" + UUID.randomUUID());
        room.setStatus(Room.Status.CREATED);
        room.setHostDisconnectPolicy(
                hostDisconnectPolicy == null ? Room.HostDisconnectPolicy.END_CALL : hostDisconnectPolicy);
        room.setSessionFlowEnabled(template.isSessionFlowEnabled());
        room.setCreatedAt(Instant.now());

        Room saved = roomRepository.save(room);

        List<TemplateRole> templateRoles = templateRoleRepository.findByTemplateIdOrderBySortOrder(templateId);
        List<RoomRole> roomRoles = templateRoles.stream()
                .map(templateRole -> toRoomRole(saved, templateRole))
                .toList();
        roomRoleRepository.saveAll(roomRoles);

        Map<UUID, RoomRole> roomRoleByTemplateRoleId = new HashMap<>();
        for (int i = 0; i < templateRoles.size(); i++) {
            roomRoleByTemplateRoleId.put(templateRoles.get(i).getId(), roomRoles.get(i));
        }

        List<TemplateStage> templateStages = templateStageRepository.findByTemplateIdOrderBySortOrder(templateId);
        List<RoomStage> roomStages = templateStages.stream()
                .map(templateStage -> toRoomStage(saved, templateStage))
                .toList();
        roomStageRepository.saveAll(roomStages);

        Map<UUID, RoomStage> roomStageByTemplateStageId = new HashMap<>();
        for (int i = 0; i < templateStages.size(); i++) {
            roomStageByTemplateStageId.put(templateStages.get(i).getId(), roomStages.get(i));
        }

        List<RoomStageRole> roomStageRoles = new ArrayList<>();
        for (TemplateStage templateStage : templateStages) {
            RoomStage roomStage = roomStageByTemplateStageId.get(templateStage.getId());
            for (TemplateStageRole templateStageRole
                    : templateStageRoleRepository.findByTemplateStageId(templateStage.getId())) {
                UUID sourceRoleId = templateStageRole.getTemplateRole().getId();
                RoomRole roomRole = roomRoleByTemplateRoleId.get(sourceRoleId);
                if (roomRole == null) {
                    throw new IllegalStateException(
                            "TemplateStage " + templateStage.getId()
                                    + " references TemplateRole " + sourceRoleId
                                    + " outside Template " + templateId);
                }

                RoomStageRole roomStageRole = new RoomStageRole();
                roomStageRole.setRoomStage(roomStage);
                roomStageRole.setRoomRole(roomRole);
                roomStageRoles.add(roomStageRole);
            }
        }
        roomStageRoleRepository.saveAll(roomStageRoles);

        return saved;
    }

    private RoomRole toRoomRole(Room room, TemplateRole templateRole) {
        RoomRole roomRole = new RoomRole();
        roomRole.setRoom(room);
        roomRole.setSourceTemplateRoleId(templateRole.getId());
        roomRole.setRoleKey(templateRole.getRoleKey());
        roomRole.setName(templateRole.getName());
        roomRole.setHostRole(templateRole.isHostRole());
        roomRole.setGuestRole(templateRole.isGuestRole());
        roomRole.setMaxMembers(templateRole.getMaxMembers());
        return roomRole;
    }

    private RoomStage toRoomStage(Room room, TemplateStage templateStage) {
        RoomStage roomStage = new RoomStage();
        roomStage.setRoom(room);
        roomStage.setSourceTemplateStageId(templateStage.getId());
        roomStage.setStageKey(templateStage.getStageKey());
        roomStage.setName(templateStage.getName());
        roomStage.setSortOrder(templateStage.getSortOrder());
        roomStage.setDurationSeconds(templateStage.getDurationSeconds());
        roomStage.setStatus(RoomStage.Status.PENDING);
        return roomStage;
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
