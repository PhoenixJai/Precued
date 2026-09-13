package com.precued.service;

import com.precued.entity.Room;
import com.precued.entity.RoomRole;
import com.precued.entity.RoomStage;
import com.precued.entity.RoomStageRole;
import com.precued.entity.Template;
import com.precued.entity.TemplateRole;
import com.precued.entity.TemplateStage;
import com.precued.entity.TemplateStageRole;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceSessionFlowSnapshotTest {

    @Mock private RoomRepository roomRepository;
    @Mock private RoomRoleRepository roomRoleRepository;
    @Mock private RoomStageRepository roomStageRepository;
    @Mock private RoomStageRoleRepository roomStageRoleRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateRoleRepository templateRoleRepository;
    @Mock private TemplateStageRepository templateStageRepository;
    @Mock private TemplateStageRoleRepository templateStageRoleRepository;
    @Mock private UserRepository userRepository;

    private RoomService service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new RoomService(
                roomRepository,
                roomRoleRepository,
                roomStageRepository,
                roomStageRoleRepository,
                templateRepository,
                templateRoleRepository,
                templateStageRepository,
                templateStageRoleRepository,
                userRepository);

        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setEmail("owner@example.com");
        owner.setDisplayName("Owner");
        CurrentUserContext.set(owner);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            if (room.getId() == null) room.setId(UUID.randomUUID());
            return room;
        });
        when(roomRoleRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<RoomRole> roles = new ArrayList<>(invocation.getArgument(0));
            roles.forEach(role -> {
                if (role.getId() == null) role.setId(UUID.randomUUID());
            });
            return roles;
        });
        when(roomStageRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<RoomStage> stages = new ArrayList<>(invocation.getArgument(0));
            stages.forEach(stage -> {
                if (stage.getId() == null) stage.setId(UUID.randomUUID());
            });
            return stages;
        });
        when(roomStageRoleRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void create_noConfiguredStages_copiesDisabledFlagAndCreatesNoStageRows() {
        Template template = template("sales_call", false);
        stubTemplate(template, List.of(), List.of());

        Room room = service.create(template.getId(), null);

        assertThat(room.isSessionFlowEnabled()).isFalse();
        verify(roomStageRepository).saveAll(List.of());
        verify(roomStageRoleRepository).saveAll(List.of());
    }

    @Test
    void create_disabledFlowWithSavedStages_stillSnapshotsPendingStages() {
        Template template = template("mock_trial", false);
        TemplateStage stage = stage(template, "opening", "Opening", 0, 120);
        stubTemplate(template, List.of(), List.of(stage));
        when(templateStageRoleRepository.findByTemplateStageId(stage.getId())).thenReturn(List.of());

        Room room = service.create(template.getId(), null);

        assertThat(room.isSessionFlowEnabled()).isFalse();
        List<RoomStage> savedStages = capturedRoomStages();
        assertThat(savedStages).singleElement().satisfies(saved -> {
            assertThat(saved.getRoom()).isSameAs(room);
            assertThat(saved.getSourceTemplateStageId()).isEqualTo(stage.getId());
            assertThat(saved.getStageKey()).isEqualTo("opening");
            assertThat(saved.getName()).isEqualTo("Opening");
            assertThat(saved.getSortOrder()).isZero();
            assertThat(saved.getDurationSeconds()).isEqualTo(120);
            assertThat(saved.getStatus()).isEqualTo(RoomStage.Status.PENDING);
            assertThat(saved.getStartedAt()).isNull();
            assertThat(saved.getCompletedAt()).isNull();
        });
    }

    @Test
    void create_enabledFlow_copiesTimedAndUntimedStagesInOrder() {
        Template template = template("ld_debate", true);
        TemplateStage timed = stage(template, "affirmative_constructive", "Affirmative Constructive", 0, 360);
        TemplateStage untimed = stage(template, "judge_notes", "Judge Notes", 1, null);
        stubTemplate(template, List.of(), List.of(timed, untimed));
        when(templateStageRoleRepository.findByTemplateStageId(timed.getId())).thenReturn(List.of());
        when(templateStageRoleRepository.findByTemplateStageId(untimed.getId())).thenReturn(List.of());

        Room room = service.create(template.getId(), null);

        assertThat(room.isSessionFlowEnabled()).isTrue();
        assertThat(capturedRoomStages())
                .extracting(RoomStage::getStageKey, RoomStage::getDurationSeconds, RoomStage::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("affirmative_constructive", 360, RoomStage.Status.PENDING),
                        org.assertj.core.groups.Tuple.tuple("judge_notes", null, RoomStage.Status.PENDING));
    }

    @Test
    void create_mapsTemplateStageRolesToTheirRoomRoleSnapshots() {
        Template template = template("mock_trial", true);
        TemplateRole judge = role(template, "judge", "Judge", true, 0);
        TemplateRole defense = role(template, "defense", "Defense", false, 1);
        TemplateStage stage = stage(template, "evidence_review", "Evidence Review", 0, null);
        TemplateStageRole judgeStageRole = stageRole(stage, judge);
        TemplateStageRole defenseStageRole = stageRole(stage, defense);

        stubTemplate(template, List.of(judge, defense), List.of(stage));
        when(templateStageRoleRepository.findByTemplateStageId(stage.getId()))
                .thenReturn(List.of(judgeStageRole, defenseStageRole));

        service.create(template.getId(), null);

        List<RoomRole> savedRoomRoles = capturedRoomRoles();
        RoomRole judgeSnapshot = savedRoomRoles.stream()
                .filter(role -> role.getSourceTemplateRoleId().equals(judge.getId()))
                .findFirst().orElseThrow();
        RoomRole defenseSnapshot = savedRoomRoles.stream()
                .filter(role -> role.getSourceTemplateRoleId().equals(defense.getId()))
                .findFirst().orElseThrow();
        RoomStage savedStage = capturedRoomStages().get(0);

        ArgumentCaptor<List<RoomStageRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(roomStageRoleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue())
                .extracting(RoomStageRole::getRoomStage, RoomStageRole::getRoomRole)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(savedStage, judgeSnapshot),
                        org.assertj.core.groups.Tuple.tuple(savedStage, defenseSnapshot));
    }

    @Test
    void create_snapshotsRemainIndependentWhenTemplateConfigurationChangesLater() {
        Template template = template("mock_trial", true);
        TemplateStage source = stage(template, "opening", "Opening Statement", 0, 120);
        stubTemplate(template, List.of(), List.of(source));
        when(templateStageRoleRepository.findByTemplateStageId(source.getId())).thenReturn(List.of());

        Room room = service.create(template.getId(), null);
        RoomStage snapshot = capturedRoomStages().get(0);

        template.setSessionFlowEnabled(false);
        source.setName("Edited Opening");
        source.setDurationSeconds(999);
        source.setSortOrder(7);

        assertThat(room.isSessionFlowEnabled()).isTrue();
        assertThat(snapshot.getName()).isEqualTo("Opening Statement");
        assertThat(snapshot.getDurationSeconds()).isEqualTo(120);
        assertThat(snapshot.getSortOrder()).isZero();
        assertThat(snapshot.getStatus()).isEqualTo(RoomStage.Status.PENDING);
    }

    private void stubTemplate(Template template, List<TemplateRole> roles, List<TemplateStage> stages) {
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(templateRoleRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(roles);
        when(templateStageRepository.findByTemplateIdOrderBySortOrder(template.getId())).thenReturn(stages);
    }

    private Template template(String id, boolean flowEnabled) {
        Template template = new Template();
        template.setId(id);
        template.setName(id);
        template.setSessionFlowEnabled(flowEnabled);
        return template;
    }

    private TemplateRole role(Template template, String key, String name, boolean host, int sortOrder) {
        TemplateRole role = new TemplateRole();
        role.setId(UUID.randomUUID());
        role.setTemplate(template);
        role.setRoleKey(key);
        role.setName(name);
        role.setHostRole(host);
        role.setGuestRole(false);
        role.setSortOrder(sortOrder);
        return role;
    }

    private TemplateStage stage(Template template, String key, String name, int sortOrder, Integer durationSeconds) {
        TemplateStage stage = new TemplateStage();
        stage.setId(UUID.randomUUID());
        stage.setTemplate(template);
        stage.setStageKey(key);
        stage.setName(name);
        stage.setSortOrder(sortOrder);
        stage.setDurationSeconds(durationSeconds);
        return stage;
    }

    private TemplateStageRole stageRole(TemplateStage stage, TemplateRole role) {
        TemplateStageRole stageRole = new TemplateStageRole();
        stageRole.setTemplateStage(stage);
        stageRole.setTemplateRole(role);
        return stageRole;
    }

    @SuppressWarnings("unchecked")
    private List<RoomRole> capturedRoomRoles() {
        ArgumentCaptor<List<RoomRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(roomRoleRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<RoomStage> capturedRoomStages() {
        ArgumentCaptor<List<RoomStage>> captor = ArgumentCaptor.forClass(List.class);
        verify(roomStageRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
