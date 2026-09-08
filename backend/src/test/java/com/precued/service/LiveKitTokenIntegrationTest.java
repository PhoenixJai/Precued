package com.precued.service;

import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.Template;
import com.precued.entity.User;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRepository;
import com.precued.repository.TemplateRepository;
import com.precued.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end regression test for a real bug found via live testing:
 * {@code GET /api/room-participants/{id}/livekit-token} threw
 * {@link org.hibernate.LazyInitializationException} because
 * LiveKitTokenService read {@code participant.getRoom().getLivekitRoomName()}
 * off a lazy Hibernate proxy outside any transaction (open-in-view is
 * disabled).
 *
 * Deliberately NOT a Mockito-based unit test: mocked repositories return
 * plain POJOs, not real Hibernate proxies, so they cannot reproduce
 * lazy-loading/session-boundary bugs at all — this exact class of bug would
 * pass every existing unit test and still break in production. This uses a
 * full Spring context, a real (in-memory) JPA-backed datasource, and MockMvc
 * hitting the real controller, so RoomParticipant.room is a genuine lazy
 * proxy loaded and released exactly as it is against Postgres in production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
class LiveKitTokenIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomParticipantRepository roomParticipantRepository;

    @Test
    void livekitToken_realJpaBackedParticipant_returns200WithoutLazyInitializationException() throws Exception {
        Template template = new Template();
        template.setId("sales_call_" + UUID.randomUUID());
        template.setName("Sales Call");
        template.setCreatedAt(Instant.now());
        templateRepository.save(template);

        User creator = new User();
        creator.setEmail(UUID.randomUUID() + "@example.com");
        creator.setDisplayName("Room Creator");
        creator.setCreatedAt(Instant.now());
        userRepository.save(creator);

        Room room = new Room();
        room.setTemplate(template);
        room.setCreatedBy(creator);
        room.setLivekitRoomName("room-" + UUID.randomUUID());
        room.setStatus(Room.Status.ACTIVE);
        room.setHostDisconnectPolicy(Room.HostDisconnectPolicy.END_CALL);
        room.setCreatedAt(Instant.now());
        Room savedRoom = roomRepository.save(room);

        RoomParticipant participant = new RoomParticipant();
        participant.setRoom(savedRoom);
        participant.setLivekitIdentity("identity-" + UUID.randomUUID());
        participant.setDisplayName("Jordan");
        participant.setAccessLevel(RoomParticipant.AccessLevel.MEMBER);
        participant.setJoinedAt(Instant.now());
        RoomParticipant savedParticipant = roomParticipantRepository.save(participant);

        // The repository call above returns to a closed persistence context
        // (open-in-view is disabled) before the request below even starts,
        // so LiveKitTokenService.issueToken's own findById() -- run under
        // its own fresh, separate transaction/session -- is what genuinely
        // reproduces the lazy proxy this bug was about.
        mockMvc.perform(get("/api/room-participants/{id}/livekit-token", savedParticipant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").value(savedRoom.getLivekitRoomName()))
                .andExpect(jsonPath("$.identity").value(savedParticipant.getLivekitIdentity()))
                .andExpect(jsonPath("$.livekitUrl").value("https://test.livekit.cloud"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
