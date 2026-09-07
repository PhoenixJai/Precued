package com.precued.service;

import com.precued.engine.VisibilityEngine;
import com.precued.entity.Share;
import com.precued.entity.ShareRoleGrant;
import com.precued.repository.RoomRoleRepository;
import com.precued.repository.ShareRepository;
import com.precued.repository.ShareRoleGrantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

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
    @Mock private VisibilityEngine engine;

    private ShareRoleGrantService service;

    @Test
    void revoke_recomputesOnlyThatOneShare() {
        service = new ShareRoleGrantService(grantRepository, shareRepository, roomRoleRepository, engine);

        UUID shareId = UUID.randomUUID();
        Share share = new Share();
        share.setId(shareId);

        UUID grantId = UUID.randomUUID();
        ShareRoleGrant grant = new ShareRoleGrant();
        grant.setId(grantId);
        grant.setShare(share);

        when(grantRepository.findById(grantId)).thenReturn(Optional.of(grant));
        when(grantRepository.save(any(ShareRoleGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.revoke(grantId);

        verify(engine).recomputeAndPushForShare(shareId);
        verify(engine, never()).recomputeAndPushForRoom(any());
    }
}
