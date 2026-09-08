package com.precued.controller;

import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.Template;
import com.precued.entity.User;
import com.precued.controller.dto.ActiveShareResponse;
import com.precued.controller.dto.RoomParticipantWithGrantsResponse;
import com.precued.service.RoomParticipantService;
import com.precued.service.RoomService;
import com.precued.service.ShareLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RoomService roomService;
    @MockBean private RoomParticipantService roomParticipantService;
    @MockBean private ShareLifecycleService shareLifecycleService;

    private Room roomWithId(UUID id) {
        Template template = new Template();
        template.setId("mock_trial");
        User createdBy = new User();
        createdBy.setId(UUID.randomUUID());

        Room room = new Room();
        room.setId(id);
        room.setTemplate(template);
        room.setCreatedBy(createdBy);
        room.setLivekitRoomName("room-" + id);
        room.setStatus(Room.Status.CREATED);
        room.setHostDisconnectPolicy(Room.HostDisconnectPolicy.END_CALL);
        room.setCreatedAt(Instant.now());
        return room;
    }

    @Test
    void create_validRequest_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(roomService.create(eq("mock_trial"), eq(userId), isNull())).thenReturn(roomWithId(roomId));

        String body = """
                {"templateId":"mock_trial","createdByUserId":"%s"}
                """.formatted(userId);

        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(roomId.toString()))
                .andExpect(jsonPath("$.templateId").value("mock_trial"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.livekitRoomName").value("room-" + roomId))
                .andExpect(jsonPath("$.livekitRoomName").isNotEmpty());
    }

    @Test
    void create_missingTemplateId_returns400() throws Exception {
        String body = """
                {"createdByUserId":"%s"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void get_existingRoom_returns200() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(roomService.get(roomId)).thenReturn(roomWithId(roomId));

        mockMvc.perform(get("/api/rooms/{id}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomId.toString()));
    }

    @Test
    void get_unknownRoom_returns404() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(roomService.get(roomId)).thenThrow(new IllegalArgumentException("No Room with id " + roomId));

        mockMvc.perform(get("/api/rooms/{id}", roomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No Room with id " + roomId));
    }

    @Test
    void listRoomRoles_existingRoom_returns200WithRoles() throws Exception {
        UUID roomId = UUID.randomUUID();
        Room room = roomWithId(roomId);

        RoomRole role = new RoomRole();
        role.setId(UUID.randomUUID());
        role.setRoom(room);
        role.setRoleKey("host");
        role.setName("Host");
        role.setHostRole(true);
        when(roomService.listRoles(roomId)).thenReturn(List.of(role));

        mockMvc.perform(get("/api/rooms/{roomId}/room-roles", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(role.getId().toString()))
                .andExpect(jsonPath("$[0].roomId").value(roomId.toString()))
                .andExpect(jsonPath("$[0].roleKey").value("host"))
                .andExpect(jsonPath("$[0].isHostRole").value(true));
    }

    @Test
    void listRoomRoles_unknownRoom_returns404() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(roomService.listRoles(roomId)).thenThrow(new IllegalArgumentException("No Room with id " + roomId));

        mockMvc.perform(get("/api/rooms/{roomId}/room-roles", roomId))
                .andExpect(status().isNotFound());
    }

    @Test
    void listRoomParticipants_existingRoom_returns200WithParticipantsAndGrants() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();

        RoomParticipantWithGrantsResponse response = new RoomParticipantWithGrantsResponse(
                participantId, roomId, null, "identity-1", "Viewer", RoomParticipant.AccessLevel.MEMBER,
                Instant.now(), null, roleId, List.of(grantId));
        when(roomParticipantService.listWithGrants(roomId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/rooms/{roomId}/room-participants", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(participantId.toString()))
                .andExpect(jsonPath("$[0].activeRoomRoleId").value(roleId.toString()))
                .andExpect(jsonPath("$[0].activeShareRoleGrantIds[0]").value(grantId.toString()));
    }

    @Test
    void listRoomParticipants_unknownRoom_returns404() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(roomParticipantService.listWithGrants(roomId))
                .thenThrow(new IllegalArgumentException("No Room with id " + roomId));

        mockMvc.perform(get("/api/rooms/{roomId}/room-participants", roomId))
                .andExpect(status().isNotFound());
    }

    @Test
    void listActiveShares_existingRoom_returns200WithSharesAndRoomRoleIds() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        ActiveShareResponse response = new ActiveShareResponse(shareId, "Exhibit A", List.of(roleId));
        when(shareLifecycleService.listActive(roomId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/rooms/{roomId}/active-shares", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shareId").value(shareId.toString()))
                .andExpect(jsonPath("$[0].label").value("Exhibit A"))
                .andExpect(jsonPath("$[0].roomRoleIds[0]").value(roleId.toString()));
    }

    @Test
    void listActiveShares_unknownRoom_returns404() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(shareLifecycleService.listActive(roomId))
                .thenThrow(new IllegalArgumentException("No Room with id " + roomId));

        mockMvc.perform(get("/api/rooms/{roomId}/active-shares", roomId))
                .andExpect(status().isNotFound());
    }
}
