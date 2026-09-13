package com.precued.controller.dto;

import com.precued.entity.Room;
import com.precued.entity.Template;
import com.precued.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomResponseTest {

    @Test
    void from_customTemplate_includesReadableTemplateName() {
        Template template = new Template();
        template.setId("3cda5ea2-915e-4a55-a0b8-dbd71cf9b3e4");
        template.setName("Negotiation Lab");

        User owner = new User();
        owner.setId(UUID.randomUUID());

        Room room = new Room();
        room.setId(UUID.randomUUID());
        room.setTemplate(template);
        room.setCreatedBy(owner);
        room.setLivekitRoomName("room-" + room.getId());
        room.setStatus(Room.Status.CREATED);
        room.setHostDisconnectPolicy(Room.HostDisconnectPolicy.END_CALL);
        room.setCreatedAt(Instant.now());

        RoomResponse response = RoomResponse.from(room);

        assertEquals("Negotiation Lab", response.templateName());
    }
}
