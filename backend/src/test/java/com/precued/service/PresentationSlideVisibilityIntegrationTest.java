package com.precued.service;

import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.entity.ShareSlide;
import com.precued.entity.Template;
import com.precued.entity.User;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRepository;
import com.precued.repository.RoomRoleRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareRoleGrantRepository;
import com.precued.repository.ShareSlideRepository;
import com.precued.repository.TemplateRepository;
import com.precued.repository.UserRepository;
import com.precued.storage.SlideImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end regression test for a real bug found via live testing: setting
 * a role's per-slide visibility to "This slide" (the only state that
 * populates ShareRoleGrant.shareSlide) made a viewer's
 * GET /api/shares/{shareId}/slides/{slideIndex}/image throw
 * LazyInitializationException — VisibilityEngineImpl.isRoleAllowed read
 * grant.getShareSlide().getSlideIndex() off a lazy Hibernate proxy outside
 * any transaction (open-in-view is disabled), the same class of bug
 * LiveKitTokenIntegrationTest already covers for a different lazy
 * association. "Always" (shareSlide == null, never dereferences the proxy)
 * and "Hidden" (no matching grant at all) never touch this code path, which
 * is why only "This slide" broke live.
 *
 * Deliberately NOT a Mockito-based unit test for the same reason as
 * LiveKitTokenIntegrationTest: mocked repositories return plain POJOs, never
 * real Hibernate proxies, so they cannot reproduce this bug at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
class PresentationSlideVisibilityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomRoleRepository roomRoleRepository;
    @Autowired private RoomParticipantRepository roomParticipantRepository;
    @Autowired private ParticipantRoleAssignmentRepository participantRoleAssignmentRepository;
    @Autowired private ShareRepository shareRepository;
    @Autowired private ShareSlideRepository shareSlideRepository;
    @Autowired private ShareRoleGrantRepository shareRoleGrantRepository;

    // Storage is irrelevant to the bug (it happens before this is ever
    // called) — mocked purely so the test doesn't need real R2 credentials
    // once the fix lets the request reach that far.
    @MockBean private SlideImageStorage slideImageStorage;

    @Test
    void viewerWithSlideSpecificGrant_fetchesTheCurrentSlide_withoutLazyInitializationException() throws Exception {
        when(slideImageStorage.download(anyString())).thenReturn(new byte[] {1, 2, 3});

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
        Room savedRoom = roomRepository.save(room);

        RoomRole viewerRole = new RoomRole();
        viewerRole.setRoom(savedRoom);
        viewerRole.setRoleKey("client");
        viewerRole.setName("Client");
        viewerRole.setHostRole(false);
        RoomRole savedViewerRole = roomRoleRepository.save(viewerRole);

        RoomParticipant host = new RoomParticipant();
        host.setRoom(savedRoom);
        host.setLivekitIdentity("host-" + UUID.randomUUID());
        host.setDisplayName("Host");
        host.setAccessLevel(RoomParticipant.AccessLevel.MEMBER);
        host.setJoinedAt(Instant.now());
        host.setSessionToken("host-token-" + UUID.randomUUID());
        RoomParticipant savedHost = roomParticipantRepository.save(host);

        RoomParticipant viewer = new RoomParticipant();
        viewer.setRoom(savedRoom);
        viewer.setLivekitIdentity("viewer-" + UUID.randomUUID());
        viewer.setDisplayName("Casey Client");
        viewer.setAccessLevel(RoomParticipant.AccessLevel.MEMBER);
        viewer.setJoinedAt(Instant.now());
        viewer.setSessionToken("viewer-token-" + UUID.randomUUID());
        RoomParticipant savedViewer = roomParticipantRepository.save(viewer);

        ParticipantRoleAssignment assignment = new ParticipantRoleAssignment();
        assignment.setRoomParticipant(savedViewer);
        assignment.setRoomRole(savedViewerRole);
        assignment.setAssignedAt(Instant.now());
        participantRoleAssignmentRepository.save(assignment);

        Share share = new Share();
        share.setRoom(savedRoom);
        share.setPublisher(savedHost);
        share.setLabel("Deck");
        share.setKind(Share.Kind.PRESENTATION);
        share.setCurrentSlideIndex(0);
        share.setStartedAt(Instant.now());
        Share savedShare = shareRepository.save(share);

        ShareSlide slide = new ShareSlide();
        slide.setShare(savedShare);
        slide.setSlideIndex(0);
        slide.setImageUrl("shares/" + savedShare.getId() + "/slides/0.png");
        slide.setCreatedAt(Instant.now());
        ShareSlide savedSlide = shareSlideRepository.save(slide);

        // The "This slide" state: a grant scoped to this one ShareSlide, not
        // a whole-share (shareSlide == null) grant.
        ShareRoleGrant grant = new ShareRoleGrant();
        grant.setShare(savedShare);
        grant.setRoomRole(savedViewerRole);
        grant.setShareSlide(savedSlide);
        grant.setGrantedAt(Instant.now());
        shareRoleGrantRepository.save(grant);

        mockMvc.perform(get("/api/shares/{shareId}/slides/{slideIndex}/image", savedShare.getId(), 0)
                        .header("Authorization", "Bearer " + savedViewer.getSessionToken()))
                .andExpect(status().isOk());
    }
}
