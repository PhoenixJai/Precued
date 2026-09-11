package com.precued.service;

import com.precued.controller.dto.ActiveShareResponse;
import com.precued.engine.VisibilityEngine;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.entity.ShareTrack;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareRoleGrantRepository;
import com.precued.repository.ShareTrackRepository;
import com.precued.repository.TemplatePresetRepository;
import com.precued.security.CurrentParticipantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the "Share started" row of the trigger table
 * (Precued_DataModel.md § "VisibilityEngine — Interface Spec"), with
 * particular attention to the host-role requirement on Share.publisher
 * ("must hold a host role" per the entity's own doc comment) — this must
 * be checked against the publisher's actual active role, never trusted
 * from the caller.
 */
@ExtendWith(MockitoExtension.class)
class ShareLifecycleServiceTest {

    @Mock private ShareRepository shareRepository;
    @Mock private ShareTrackRepository shareTrackRepository;
    @Mock private RoomParticipantRepository roomParticipantRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ParticipantRoleAssignmentRepository assignmentRepository;
    @Mock private ShareRoleGrantRepository shareRoleGrantRepository;
    @Mock private TemplatePresetRepository templatePresetRepository;
    @Mock private VisibilityEngine engine;

    private ShareLifecycleService service;

    private final UUID roomId = UUID.randomUUID();
    private final UUID publisherId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ShareLifecycleService(
                shareRepository,
                shareTrackRepository,
                roomParticipantRepository,
                roomRepository,
                assignmentRepository,
                shareRoleGrantRepository,
                templatePresetRepository,
                engine);

        // Every test below acts as publisherId — matches the only real
        // caller pattern (self-start/self-end a Share), enforced in the service.
        RoomParticipant self = new RoomParticipant();
        self.setId(publisherId);
        CurrentParticipantContext.set(self);
    }

    @AfterEach
    void clearAuthentication() {
        CurrentParticipantContext.clear();
    }

    private RoomParticipant givenPublisherInRoom() {
        Room room = new Room();
        room.setId(roomId);

        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);
        publisher.setRoom(room);

        when(roomParticipantRepository.findById(publisherId)).thenReturn(Optional.of(publisher));
        return publisher;
    }

    @Test
    void start_createsShareAndRecomputes_whenPublisherHoldsHostRole() {
        givenPublisherInRoom();

        RoomRole hostRole = new RoomRole();
        hostRole.setHostRole(true);
        ParticipantRoleAssignment assignment = new ParticipantRoleAssignment();
        assignment.setRoomRole(hostRole);
        when(assignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(publisherId))
                .thenReturn(Optional.of(assignment));

        UUID shareId = UUID.randomUUID();
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> {
            Share share = invocation.getArgument(0);
            share.setId(shareId);
            return share;
        });

        Share result = service.start(roomId, publisherId, null, "Exhibit A");

        assertThat(result.getId()).isEqualTo(shareId);
        assertThat(result.getStatus()).isEqualTo(Share.Status.ACTIVE);
        assertThat(result.getStartedAt()).isNotNull();
        assertThat(result.getLabel()).isEqualTo("Exhibit A");
        assertThat(result.getAppliedPreset()).isNull();
        verify(engine).recomputeAndPushForShare(shareId);
    }

    @Test
    void start_rejectsPublisherWithNonHostRole_andDoesNotCreateAShare() {
        givenPublisherInRoom();

        RoomRole memberRole = new RoomRole();
        memberRole.setHostRole(false);
        ParticipantRoleAssignment assignment = new ParticipantRoleAssignment();
        assignment.setRoomRole(memberRole);
        when(assignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(publisherId))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.start(roomId, publisherId, null, "Exhibit A"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not hold a host role");

        verify(shareRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }

    @Test
    void start_rejectsPublisherWithNoActiveAssignment_andDoesNotCreateAShare() {
        givenPublisherInRoom();
        when(assignmentRepository.findByRoomParticipantIdAndRevokedAtIsNull(publisherId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(roomId, publisherId, null, "Exhibit A"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no active role assignment");

        verify(shareRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }

    @Test
    void start_onBehalfOfAnotherParticipant_rejectsAndDoesNotCreateAShare() {
        givenPublisherInRoom();

        RoomParticipant someoneElse = new RoomParticipant();
        someoneElse.setId(UUID.randomUUID());
        CurrentParticipantContext.set(someoneElse);

        assertThatThrownBy(() -> service.start(roomId, publisherId, null, "Exhibit A"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot start a Share on behalf of another participant");

        verify(shareRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }

    @Test
    void end_byNonPublisher_rejectsAndDoesNotEndTheShare() {
        UUID shareId = UUID.randomUUID();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);
        Share share = new Share();
        share.setId(shareId);
        share.setStatus(Share.Status.ACTIVE);
        share.setPublisher(publisher);
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));

        RoomParticipant someoneElse = new RoomParticipant();
        someoneElse.setId(UUID.randomUUID());
        CurrentParticipantContext.set(someoneElse);

        assertThatThrownBy(() -> service.end(shareId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only the Share's publisher can end it");

        verify(shareRepository, never()).save(any());
    }

    @Test
    void end_setsEndedStatusAndUnpublishesEveryTrack_withNoEngineCall() {
        UUID shareId = UUID.randomUUID();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);
        Share share = new Share();
        share.setId(shareId);
        share.setStatus(Share.Status.ACTIVE);
        share.setPublisher(publisher);
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        when(shareRepository.save(any(Share.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareTrack videoTrack = new ShareTrack();
        ShareTrack alreadyUnpublished = new ShareTrack();
        alreadyUnpublished.setUnpublishedAt(Instant.now().minusSeconds(60));
        when(shareTrackRepository.findByShareId(shareId)).thenReturn(List.of(videoTrack, alreadyUnpublished));

        Share result = service.end(shareId);

        assertThat(result.getStatus()).isEqualTo(Share.Status.ENDED);
        assertThat(result.getEndedAt()).isNotNull();
        assertThat(videoTrack.getUnpublishedAt()).isNotNull();
        verify(shareTrackRepository).save(videoTrack);
        // Already-unpublished track is left untouched, not re-saved with a new timestamp.
        verify(shareTrackRepository, never()).save(alreadyUnpublished);
        verify(engine, never()).recomputeAndPushForShare(any());
        verify(engine, never()).recomputeAndPushForRoom(any());
    }

    @Test
    void listActive_returnsActiveSharesWithTheirGrantedRoomRoleIds() {
        when(roomRepository.existsById(roomId)).thenReturn(true);

        Share share = new Share();
        share.setId(UUID.randomUUID());
        share.setLabel("Exhibit A");
        when(shareRepository.findByRoomIdAndStatus(roomId, Share.Status.ACTIVE)).thenReturn(List.of(share));

        UUID roleId = UUID.randomUUID();
        RoomRole role = new RoomRole();
        role.setId(roleId);
        ShareRoleGrant grant = new ShareRoleGrant();
        grant.setRoomRole(role);
        when(shareRoleGrantRepository.findByShareIdAndRevokedAtIsNull(share.getId())).thenReturn(List.of(grant));

        List<ActiveShareResponse> result = service.listActive(roomId);

        assertThat(result).hasSize(1);
        ActiveShareResponse response = result.get(0);
        assertThat(response.id()).isEqualTo(share.getId());
        assertThat(response.label()).isEqualTo("Exhibit A");
        assertThat(response.roomRoleIds()).containsExactly(roleId);
    }

    @Test
    void listActive_unknownRoom_throwsIllegalArgumentException() {
        UUID unknownRoomId = UUID.randomUUID();
        when(roomRepository.existsById(unknownRoomId)).thenReturn(false);

        assertThatThrownBy(() -> service.listActive(unknownRoomId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(unknownRoomId.toString());
    }

    // ===== changeSlide — Chunk 1, Precued_DataModel.md "Presentations Feature" =====

    private Share givenPresentationShare(UUID shareId) {
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);
        Share share = new Share();
        share.setId(shareId);
        share.setStatus(Share.Status.ACTIVE);
        share.setPublisher(publisher);
        share.setKind(Share.Kind.PRESENTATION);
        share.setCurrentSlideIndex(0);
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        org.mockito.Mockito.lenient()
                .when(shareRepository.save(any(Share.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return share;
    }

    @Test
    void changeSlide_updatesIndexAndRecomputesForThatShare_whenCalledByPublisher() {
        UUID shareId = UUID.randomUUID();
        givenPresentationShare(shareId);

        Share result = service.changeSlide(shareId, 2);

        assertThat(result.getCurrentSlideIndex()).isEqualTo(2);
        verify(engine).recomputeAndPushForShare(shareId);
    }

    @Test
    void changeSlide_byNonPublisher_rejectsAndDoesNotUpdateOrRecompute() {
        UUID shareId = UUID.randomUUID();
        givenPresentationShare(shareId);

        RoomParticipant someoneElse = new RoomParticipant();
        someoneElse.setId(UUID.randomUUID());
        CurrentParticipantContext.set(someoneElse);

        assertThatThrownBy(() -> service.changeSlide(shareId, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only the Share's publisher can change its slide");

        verify(shareRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }

    @Test
    void changeSlide_onScreenKindShare_rejectsAndDoesNotUpdateOrRecompute() {
        UUID shareId = UUID.randomUUID();
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);
        Share share = new Share();
        share.setId(shareId);
        share.setStatus(Share.Status.ACTIVE);
        share.setPublisher(publisher);
        // kind defaults to SCREEN
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));

        assertThatThrownBy(() -> service.changeSlide(shareId, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only a presentation-kind Share has slides");

        verify(shareRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }
}
