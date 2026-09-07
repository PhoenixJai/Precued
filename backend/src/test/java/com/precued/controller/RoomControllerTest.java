package com.precued.controller;

import com.precued.entity.Room;
import com.precued.entity.Template;
import com.precued.entity.User;
import com.precued.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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
                .andExpect(jsonPath("$.status").value("CREATED"));
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
}
