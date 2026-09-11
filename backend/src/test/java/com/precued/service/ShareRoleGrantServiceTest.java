package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.entity.ShareSlide;
import com.precued.repository.RoomRoleRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareRoleGrantRepository;
import com.precued.repository.ShareSlideRepository;
import com.precued.security.CurrentParticipantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the ShareRoleGrant row of the trigger table (Precued_DataModel.md
 * § "VisibilityEngine — Interface Spec"): a grant change recomputes only
 * the one Share it belongs to, never the whole room.
 */
@ExtendWith(MockitoExtension.class)
class ShareRoleGrantServiceTest {

    @Mock private ShareRoleGrantRepository grantRepository;
    @Mock private ShareRepository shareRepository;
    @Mock private RoomRoleRepository roomRoleRepository;
    @Mock private ShareSlideRepository shareSlideRepository;
    @Mock private VisibilityEngine engine;

    private ShareRoleGrantService service;

    private final UUID publisherId = UUID.randomUUID();

    @AfterEach
    void clearAuthentication() {
        CurrentParticipantContext.clear();
    }

    private void authenticateAsPublisher() {
        RoomParticipant self = new RoomParticipant();
        self.setId(publisherId);
        CurrentParticipantContext.set(self);
    }

    private Share shareWithPublisher(UUID shareId) {
        RoomParticipant publisher = new RoomParticipant();
        publisher.setId(publisherId);
        Share share = new Share();
        share.setId(shareId);
        share.setPublisher(publisher);
        return share;
    }

    @Test
    void revoke_recomputesOnlyThatOneShare() {
        service = new ShareRoleGrantService(
                grantRepository, shareRepository, roomRoleRepository, shareSlideRepository, engine);

        UUID shareId = UUID.randomUUID();
        Share share = shareWithPublisher(shareId);

        UUID grantId = UUID.randomUUID();
        ShareRoleGrant grant = new ShareRoleGrant();
        grant.setId(grantId);
        grant.setShare(share);

        when(grantRepository.findById(grantId)).thenReturn(Optional.of(grant));
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        when(grantRepository.save(any(ShareRoleGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAsPublisher();

        service.revoke(grantId);

        verify(engine).recomputeAndPushForShare(shareId);
        verify(engine, never()).recomputeAndPushForRoom(any());
    }

    @Test
    void grant_byNonPublisher_rejectsAndDoesNotCreateGrant() {
        service = new ShareRoleGrantService(
                grantRepository, shareRepository, roomRoleRepository, shareSlideRepository, engine);

        UUID shareId = UUID.randomUUID();
        Share share = shareWithPublisher(shareId);
        UUID roleId = UUID.randomUUID();
        RoomRole role = new RoomRole();
        role.setId(roleId);

        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        when(roomRoleRepository.findById(roleId)).thenReturn(Optional.of(role));

        RoomParticipant someoneElse = new RoomParticipant();
        someoneElse.setId(UUID.randomUUID());
        CurrentParticipantContext.set(someoneElse);

        assertThatThrownBy(() -> service.grant(shareId, roleId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only the Share's publisher can grant visibility to it");

        verify(grantRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }

    @Test
    void revoke_byNonPublisher_rejectsAndDoesNotRevokeGrant() {
        service = new ShareRoleGrantService(
                grantRepository, shareRepository, roomRoleRepository, shareSlideRepository, engine);

        UUID shareId = UUID.randomUUID();
        Share share = shareWithPublisher(shareId);
        UUID grantId = UUID.randomUUID();
        ShareRoleGrant grant = new ShareRoleGrant();
        grant.setId(grantId);
        grant.setShare(share);

        when(grantRepository.findById(grantId)).thenReturn(Optional.of(grant));
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));

        RoomParticipant someoneElse = new RoomParticipant();
        someoneElse.setId(UUID.randomUUID());
        CurrentParticipantContext.set(someoneElse);

        assertThatThrownBy(() -> service.revoke(grantId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only the Share's publisher can revoke visibility on it");

        verify(grantRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }

    // ===== slide-specific grants — Chunk 3, Precued_DataModel.md "Presentations Feature" =====

    @Test
    void grant_withShareSlideId_createsSlideSpecificGrant() {
        service = new ShareRoleGrantService(
                grantRepository, shareRepository, roomRoleRepository, shareSlideRepository, engine);

        UUID shareId = UUID.randomUUID();
        Share share = shareWithPublisher(shareId);
        UUID roleId = UUID.randomUUID();
        RoomRole role = new RoomRole();
        role.setId(roleId);
        UUID slideId = UUID.randomUUID();
        ShareSlide slide = new ShareSlide();
        slide.setId(slideId);
        slide.setShare(share);

        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        when(roomRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(shareSlideRepository.findById(slideId)).thenReturn(Optional.of(slide));
        when(grantRepository.save(any(ShareRoleGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAsPublisher();

        ArgumentCaptor<ShareRoleGrant> captor = ArgumentCaptor.forClass(ShareRoleGrant.class);
        service.grant(shareId, roleId, slideId);

        verify(grantRepository).save(captor.capture());
        assertThat(captor.getValue().getShareSlide()).isSameAs(slide);
        verify(engine).recomputeAndPushForShare(shareId);
    }

    @Test
    void grant_withNullShareSlideId_behavesExactlyLikeTheTwoArgOverload() {
        service = new ShareRoleGrantService(
                grantRepository, shareRepository, roomRoleRepository, shareSlideRepository, engine);

        UUID shareId = UUID.randomUUID();
        Share share = shareWithPublisher(shareId);
        UUID roleId = UUID.randomUUID();
        RoomRole role = new RoomRole();
        role.setId(roleId);

        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        when(roomRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(grantRepository.save(any(ShareRoleGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authenticateAsPublisher();

        ArgumentCaptor<ShareRoleGrant> captor = ArgumentCaptor.forClass(ShareRoleGrant.class);
        service.grant(shareId, roleId, null);

        verify(grantRepository).save(captor.capture());
        assertThat(captor.getValue().getShareSlide()).isNull();
        verify(shareSlideRepository, never()).findById(any());
    }

    @Test
    void grant_withShareSlideIdBelongingToADifferentShare_rejectsWithoutSavingAGrant() {
        service = new ShareRoleGrantService(
                grantRepository, shareRepository, roomRoleRepository, shareSlideRepository, engine);

        UUID shareId = UUID.randomUUID();
        Share share = shareWithPublisher(shareId);
        UUID roleId = UUID.randomUUID();
        RoomRole role = new RoomRole();
        role.setId(roleId);

        UUID slideId = UUID.randomUUID();
        Share otherShare = new Share();
        otherShare.setId(UUID.randomUUID());
        ShareSlide slideFromAnotherShare = new ShareSlide();
        slideFromAnotherShare.setId(slideId);
        slideFromAnotherShare.setShare(otherShare);

        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        when(roomRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(shareSlideRepository.findById(slideId)).thenReturn(Optional.of(slideFromAnotherShare));
        authenticateAsPublisher();

        assertThatThrownBy(() -> service.grant(shareId, roleId, slideId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to Share " + shareId);

        verify(grantRepository, never()).save(any());
        verify(engine, never()).recomputeAndPushForShare(any());
    }

    @Test
    void grant_withUnknownShareSlideId_returnsNotFound() {
        service = new ShareRoleGrantService(
                grantRepository, shareRepository, roomRoleRepository, shareSlideRepository, engine);

        UUID shareId = UUID.randomUUID();
        Share share = shareWithPublisher(shareId);
        UUID roleId = UUID.randomUUID();
        RoomRole role = new RoomRole();
        role.setId(roleId);
        UUID slideId = UUID.randomUUID();

        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        when(roomRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(shareSlideRepository.findById(slideId)).thenReturn(Optional.empty());
        authenticateAsPublisher();

        assertThatThrownBy(() -> service.grant(shareId, roleId, slideId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No ShareSlide with id " + slideId);

        verify(grantRepository, never()).save(any());
    }
}
