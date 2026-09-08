package com.precued.service;

import com.precued.entity.Room;
import com.precued.entity.RoomRole;
import com.precued.entity.Template;
import com.precued.entity.TemplateRole;
import com.precued.entity.User;
import com.precued.repository.RoomRepository;
import com.precued.repository.RoomRoleRepository;
import com.precued.repository.TemplateRepository;
import com.precued.repository.TemplateRoleRepository;
import com.precued.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers RoomService#create's role-copy step, per Precued_DataModel.md
 * Decision #1: "Roles are copied from Template into Room at creation, not
 * referenced live." Regression coverage for a real bug found via live
 * testing — room creation saved the Room but never created any RoomRole
 * rows at all, leaving every room with zero roles and no way to ever
 * assign a host.
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomRoleRepository roomRoleRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateRoleRepository templateRoleRepository;
    @Mock private UserRepository userRepository;

    private RoomService service;

    private final String templateId = "sales_call";
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RoomService(
                roomRepository, roomRoleRepository, templateRepository, templateRoleRepository, userRepository);

        Template template = new Template();
        template.setId(templateId);
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        User user = new User();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(UUID.randomUUID());
            return room;
        });
    }

    @Test
    void create_copiesEveryTemplateRoleIntoARoomRole_withCorrectHostFlagsAndTraceability() {
        TemplateRole salesRep = templateRole("sales_rep", "Sales Rep", true, null);
        TemplateRole prospect = templateRole("prospect", "Prospect", false, 1);
        TemplateRole observer = templateRole("observer", "Observer", false, null);
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(templateId))
                .thenReturn(List.of(salesRep, prospect, observer));

        Room room = service.create(templateId, userId, null);

        ArgumentCaptor<List<RoomRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(roomRoleRepository).saveAll(captor.capture());
        List<RoomRole> savedRoles = captor.getValue();

        // Exactly one RoomRole per TemplateRole under the template — the
        // core assertion for the bug: room_role must not stay empty.
        assertThat(savedRoles).hasSize(3);
        assertThat(savedRoles).allMatch(role -> role.getRoom() == room);

        RoomRole savedSalesRep = savedRoles.get(0);
        assertThat(savedSalesRep.getRoleKey()).isEqualTo("sales_rep");
        assertThat(savedSalesRep.getName()).isEqualTo("Sales Rep");
        assertThat(savedSalesRep.isHostRole()).isTrue();
        assertThat(savedSalesRep.getSourceTemplateRoleId()).isEqualTo(salesRep.getId());
        assertThat(savedSalesRep.getMaxMembers()).isNull();

        RoomRole savedProspect = savedRoles.get(1);
        assertThat(savedProspect.getRoleKey()).isEqualTo("prospect");
        assertThat(savedProspect.isHostRole()).isFalse();
        assertThat(savedProspect.getMaxMembers()).isEqualTo(1);

        RoomRole savedObserver = savedRoles.get(2);
        assertThat(savedObserver.getRoleKey()).isEqualTo("observer");
        assertThat(savedObserver.isHostRole()).isFalse();

        // Exactly one host role among the copied set — matches the seeded
        // sales_call template (Sales Rep is the only host role).
        assertThat(savedRoles).filteredOn(RoomRole::isHostRole).hasSize(1);
    }

    @Test
    void create_templateWithNoRoles_savesEmptyRoomRoleList() {
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(templateId)).thenReturn(List.of());

        service.create(templateId, userId, null);

        ArgumentCaptor<List<RoomRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(roomRoleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    private TemplateRole templateRole(String roleKey, String name, boolean isHostRole, Integer maxMembers) {
        TemplateRole role = new TemplateRole();
        role.setId(UUID.randomUUID());
        role.setRoleKey(roleKey);
        role.setName(name);
        role.setHostRole(isHostRole);
        role.setMaxMembers(maxMembers);
        return role;
    }
}
