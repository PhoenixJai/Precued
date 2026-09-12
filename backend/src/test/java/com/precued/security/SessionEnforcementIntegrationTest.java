package com.precued.security;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Precued_Issues_Update_3.md, M-Auth item 2's AC, verified end-to-end
 * against a real Spring Security filter chain (SecurityConfig) and real
 * JPA-backed entities — not mocks, since the whole point is confirming the
 * framework-level 401 gate actually runs before a controller/service ever
 * sees the request.
 *
 * Deliberately does NOT also re-test OPTIONS-preflight-bypasses-auth here:
 * WebMvcConfigCorsTest and RoomControllerCorsPreflightTest already exercise
 * that against this same real SecurityConfig bean (via @Import) with a
 * properly configured allowed-origin, which this test's H2/integrationtest
 * profile doesn't set.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
class SessionEnforcementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomParticipantRepository roomParticipantRepository;

    @Test
    void writeEndpoint_noSessionAtAll_rejected401BeforeReachingTheController() throws Exception {
        // No Authorization header whatsoever — the AC's literal "no valid
        // session -> 401, regardless of whether the caller knows a valid
        // UUID" case. A garbage/incomplete body is fine: authentication
        // happens before the body is ever parsed.
        mockMvc.perform(post("/api/participant-role-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestJoin_stillWorksWithNoSessionAtAll_hybridAuthUnbroken() throws Exception {
        // The one thing this whole migration must never break: a guest join
        // sends no token by design (see AuthSessionInterceptor's Javadoc)
        // and must not be caught by the new "authenticated()" gate — this
        // exact path is one of SecurityConfig's permitAll entries.
        Room room = persistRoom();

        mockMvc.perform(post("/api/room-participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"" + room.getId() + "\",\"userId\":null,\"displayName\":\"Guest\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void validSessionForADifferentRoom_stillRejected403_ownershipCheckSurvivedTheMigration() throws Exception {
        Room ownRoom = persistRoom();
        Room otherRoom = persistRoom();
        RoomParticipant participant = persistParticipant(ownRoom);

        mockMvc.perform(get("/api/rooms/{roomId}/active-shares", otherRoom.getId())
                        .header("Authorization", "Bearer " + participant.getSessionToken()))
                .andExpect(status().isForbidden());
    }

    private Room persistRoom() {
        Template template = new Template();
        template.setId("sales_call_" + UUID.randomUUID());
        template.setName("Sales Call");
        template.setCreatedAt(Instant.now());
        templateRepository.save(template);

        User creator = new User();
        creator.setEmail(UUID.randomUUID() + "@example.com");
        creator.setDisplayName("Host");
        creator.setCreatedAt(Instant.now());
        userRepository.save(creator);

        Room room = new Room();
        room.setTemplate(template);
        room.setCreatedBy(creator);
        room.setLivekitRoomName("room-" + UUID.randomUUID());
        room.setStatus(Room.Status.ACTIVE);
        room.setHostDisconnectPolicy(Room.HostDisconnectPolicy.END_CALL);
        room.setCreatedAt(Instant.now());
        return roomRepository.save(room);
    }

    private RoomParticipant persistParticipant(Room room) {
        RoomParticipant participant = new RoomParticipant();
        participant.setRoom(room);
        participant.setLivekitIdentity("identity-" + UUID.randomUUID());
        participant.setDisplayName("Jordan");
        participant.setAccessLevel(RoomParticipant.AccessLevel.MEMBER);
        participant.setJoinedAt(Instant.now());
        participant.setSessionToken("session-token-" + UUID.randomUUID());
        return roomParticipantRepository.save(participant);
    }
}
