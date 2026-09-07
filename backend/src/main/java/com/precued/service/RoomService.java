package com.precued.service;

import com.precued.entity.Room;
import com.precued.entity.Template;
import com.precued.entity.User;
import com.precued.repository.RoomRepository;
import com.precued.repository.TemplateRepository;
import com.precued.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final TemplateRepository templateRepository;
    private final UserRepository userRepository;

    public RoomService(
            RoomRepository roomRepository, TemplateRepository templateRepository, UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
    }

    /** hostDisconnectPolicy defaults to END_CALL (Precued_DataModel.md) when null. */
    public Room create(String templateId, UUID createdByUserId, Room.HostDisconnectPolicy hostDisconnectPolicy) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("No Template with id " + templateId));
        User createdBy = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new IllegalArgumentException("No User with id " + createdByUserId));

        Room room = new Room();
        room.setTemplate(template);
        room.setCreatedBy(createdBy);
        room.setLivekitRoomName("room-" + UUID.randomUUID());
        room.setStatus(Room.Status.CREATED);
        room.setHostDisconnectPolicy(
                hostDisconnectPolicy == null ? Room.HostDisconnectPolicy.END_CALL : hostDisconnectPolicy);
        room.setCreatedAt(Instant.now());

        return roomRepository.save(room);
    }

    public Room get(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("No Room with id " + roomId));
    }
}
