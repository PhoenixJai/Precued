package com.precued.service;

import com.precued.entity.Room;
import com.precued.entity.RoomRole;
import com.precued.entity.Template;
import com.precued.entity.TemplateRole;
import com.precued.entity.User;
import com.precued.repository.RoomRepository;
import com.precued.repository.RoomRoleRepository;
import com.precued.repository.RoomStageRepository;
import com.precued.repository.RoomStageRoleRepository;
import com.precued.repository.TemplateRepository;
import com.precued.repository.TemplateRoleRepository;
import com.precued.repository.TemplateStageRepository;
import com.precued.repository.TemplateStageRoleRepository;
import com.precued.repository.UserRepository;
import com.precued.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceSnapshotHygieneTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomRoleRepository roomRoleRepository;
    @Mock private RoomStageRepository roomStageRepository;
    @Mock private RoomStageRoleRepository roomStageRoleRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateRoleRepository templateRoleRepository;
    @Mock private TemplateStageRepository templateStageRepository;
    @Mock private TemplateStageRoleRepository templateStageRoleRepository;
    @Mock private UserRepository userRepository;

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void create_snapshotsGuestRoleFlagIntoRoomRole() {
        User owner = user();
        CurrentUserContext.set(owner);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        Template template = builtInTemplate("mock_trial");
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateStageRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of());

        TemplateRole guest = new TemplateRole();
        guest.setId(UUID.randomUUID());
        guest.setTemplate(template);
        guest.setRoleKey("observer");
        guest.setName("Observer");
        guest.setGuestRole(true);
        guest.setHostRole(false);
        guest.setSortOrder(0);
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of(guest));

        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(UUID.randomUUID());
            return room;
        });

        RoomService service = service();
        service.create(template.getId(), null);

        ArgumentCaptor<List<RoomRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(roomRoleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(roomRole -> {
            assertThat(roomRole.isGuestRole()).isTrue();
            assertThat(roomRole.getSourceTemplateRoleId()).isEqualTo(guest.getId());
        });
    }

    @Test
    void create_ownedCustomTemplate_isAllowed() {
        User owner = user();
        CurrentUserContext.set(owner);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        Template template = customTemplate(owner);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of());
        when(templateStageRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of());
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(UUID.randomUUID());
            return room;
        });

        Room room = service().create(template.getId(), null);

        assertThat(room.getTemplate()).isSameAs(template);
        assertThat(room.getCreatedBy().getId()).isEqualTo(owner.getId());
    }

    @Test
    void create_customTemplateOwnedByAnotherUser_isRejectedAsNotFoundBeforePersistence() {
        User caller = user();
        User otherOwner = user();
        CurrentUserContext.set(caller);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));

        Template template = customTemplate(otherOwner);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service().create(template.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No Template with id " + template.getId());

        verify(roomRepository, never()).save(any(Room.class));
        verify(roomRoleRepository, never()).saveAll(any());
        verify(roomStageRepository, never()).saveAll(any());
    }

    @Test
    void create_builtinTemplate_remainsAvailableToAuthenticatedUsers() {
        User caller = user();
        CurrentUserContext.set(caller);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));

        Template template = builtInTemplate("sales_call");
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of());
        when(templateStageRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(List.of());
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(UUID.randomUUID());
            return room;
        });

        assertThat(service().create(template.getId(), null).getTemplate()).isSameAs(template);
    }

    private RoomService service() {
        return new RoomService(
                roomRepository,
                roomRoleRepository,
                roomStageRepository,
                roomStageRoleRepository,
                templateRepository,
                templateRoleRepository,
                templateStageRepository,
                templateStageRoleRepository,
                userRepository);
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setDisplayName("User");
        return user;
    }

    private Template builtInTemplate(String id) {
        Template template = new Template();
        template.setId(id);
        template.setName(id);
        return template;
    }

    private Template customTemplate(User owner) {
        Template template = new Template();
        template.setId(UUID.randomUUID().toString());
        template.setName("Custom");
        template.setCreatedBy(owner);
        return template;
    }
}
