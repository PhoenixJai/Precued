package com.precued.service;

import com.precued.controller.dto.SessionFlowResponse;
import com.precued.entity.ParticipantRoleAssignment;
import com.precued.entity.Room;
import com.precued.entity.RoomParticipant;
import com.precued.entity.RoomRole;
import com.precued.entity.Template;
import com.precued.entity.TemplateRole;
import com.precued.entity.User;
import com.precued.repository.ParticipantRoleAssignmentRepository;
import com.precued.repository.RoomParticipantRepository;
import com.precued.repository.RoomRoleRepository;
import com.precued.repository.TemplateRepository;
import com.precued.repository.TemplateRoleRepository;
import com.precued.repository.UserRepository;
import com.precued.security.CurrentParticipantContext;
import com.precued.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the first Precued-owned Session Flow as one coherent vertical slice:
 * the V10 migration seeds Mock Trial configuration, RoomService snapshots it
 * into runtime RoomStage/RoomStageRole rows, and SessionFlowService can drive
 * that snapshot from NOT_STARTED through the final COMPLETED state.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integrationtest")
class MockTrialSessionFlowSeedIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private TemplateRoleRepository templateRoleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRoleRepository roomRoleRepository;
    @Autowired private RoomParticipantRepository roomParticipantRepository;
    @Autowired private ParticipantRoleAssignmentRepository assignmentRepository;
    @Autowired private RoomService roomService;
    @Autowired private SessionFlowService sessionFlowService;

    @AfterEach
    void clearContexts() {
        CurrentParticipantContext.clear();
        CurrentUserContext.clear();
    }

    @Test
    void seededMockTrialFlow_snapshotsAndRunsThroughAllEightStages() throws Exception {
        Template mockTrial = new Template();
        mockTrial.setId("mock_trial");
        mockTrial.setName("Mock Trial");
        mockTrial.setSessionFlowEnabled(false);
        mockTrial.setCreatedAt(Instant.now());
        templateRepository.save(mockTrial);

        List<TemplateRole> sourceRoles = List.of(
                role(mockTrial, "judge", "Judge", true, 0),
                role(mockTrial, "jury", "Jury", false, 1),
                role(mockTrial, "defense", "Defense", false, 2),
                role(mockTrial, "prosecution", "Prosecution", false, 3));
        templateRoleRepository.saveAll(sourceRoles);

        // Flyway is disabled for the H2 integration profile because older
        // migrations use Postgres-only syntax. Execute V10 itself directly so
        // this test proves the actual seed SQL rather than duplicating the
        // stage configuration in Java.
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V10__seed_mock_trial_session_flow.sql"));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        Template seededTemplate = templateRepository.findById("mock_trial").orElseThrow();
        assertThat(seededTemplate.isSessionFlowEnabled()).isTrue();

        List<Map<String, Object>> seededStages = query("""
                SELECT stage_key, name, sort_order, duration_seconds
                FROM template_stage
                WHERE template_id = 'mock_trial'
                ORDER BY sort_order
                """);
        assertThat(seededStages).hasSize(8);
        assertThat(seededStages)
                .extracting(row -> row.get("STAGE_KEY"), row -> row.get("NAME"),
                        row -> row.get("SORT_ORDER"), row -> row.get("DURATION_SECONDS"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("prosecution_opening", "Prosecution Opening", 0, 120),
                        org.assertj.core.groups.Tuple.tuple("defense_opening", "Defense Opening", 1, 120),
                        org.assertj.core.groups.Tuple.tuple("judge_questions", "Judge Questions", 2, null),
                        org.assertj.core.groups.Tuple.tuple("evidence_review", "Evidence Review", 3, null),
                        org.assertj.core.groups.Tuple.tuple("prosecution_closing", "Prosecution Closing", 4, 120),
                        org.assertj.core.groups.Tuple.tuple("defense_closing", "Defense Closing", 5, 120),
                        org.assertj.core.groups.Tuple.tuple("jury_deliberation", "Jury Deliberation", 6, 300),
                        org.assertj.core.groups.Tuple.tuple("verdict", "Verdict", 7, null));

        assertThat(stageRoleKeys())
                .containsEntry("prosecution_opening", List.of("prosecution"))
                .containsEntry("defense_opening", List.of("defense"))
                .containsEntry("judge_questions", List.of("judge"))
                .containsEntry("evidence_review", List.of("defense", "judge", "prosecution"))
                .containsEntry("prosecution_closing", List.of("prosecution"))
                .containsEntry("defense_closing", List.of("defense"))
                .containsEntry("jury_deliberation", List.of("jury"))
                .containsEntry("verdict", List.of("judge", "jury"));

        User owner = new User();
        owner.setEmail("mock-trial-host-" + UUID.randomUUID() + "@example.com");
        owner.setDisplayName("Judge Host");
        owner.setCreatedAt(Instant.now());
        owner = userRepository.save(owner);
        CurrentUserContext.set(owner);

        Room room = roomService.create("mock_trial", null);
        assertThat(room.isSessionFlowEnabled()).isTrue();

        List<RoomRole> roomRoles = roomRoleRepository.findByRoomId(room.getId());
        RoomRole judgeRole = roomRoles.stream()
                .filter(role -> role.getRoleKey().equals("judge"))
                .findFirst().orElseThrow();

        RoomParticipant host = new RoomParticipant();
        host.setRoom(room);
        host.setUser(owner);
        host.setLivekitIdentity("judge-" + UUID.randomUUID());
        host.setDisplayName("Judge Host");
        host.setAccessLevel(RoomParticipant.AccessLevel.HOST);
        host.setJoinedAt(Instant.now());
        host.setSessionToken("session-" + UUID.randomUUID());
        host = roomParticipantRepository.save(host);

        ParticipantRoleAssignment assignment = new ParticipantRoleAssignment();
        assignment.setRoomParticipant(host);
        assignment.setRoomRole(judgeRole);
        assignment.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment);
        CurrentParticipantContext.set(host);

        SessionFlowResponse beforeStart = sessionFlowService.get(room.getId());
        assertThat(beforeStart.status()).isEqualTo(SessionFlowResponse.FlowStatus.NOT_STARTED);
        assertThat(beforeStart.stages()).extracting(SessionFlowResponse.Stage::stageKey)
                .containsExactly(
                        "prosecution_opening",
                        "defense_opening",
                        "judge_questions",
                        "evidence_review",
                        "prosecution_closing",
                        "defense_closing",
                        "jury_deliberation",
                        "verdict");

        SessionFlowResponse running = sessionFlowService.start(room.getId());
        assertThat(running.status()).isEqualTo(SessionFlowResponse.FlowStatus.IN_PROGRESS);
        assertThat(currentStageKey(running)).isEqualTo("prosecution_opening");

        String[] remaining = {
                "defense_opening",
                "judge_questions",
                "evidence_review",
                "prosecution_closing",
                "defense_closing",
                "jury_deliberation",
                "verdict"
        };
        for (String expectedStage : remaining) {
            running = sessionFlowService.advance(room.getId());
            assertThat(running.status()).isEqualTo(SessionFlowResponse.FlowStatus.IN_PROGRESS);
            assertThat(currentStageKey(running)).isEqualTo(expectedStage);
        }

        SessionFlowResponse completed = sessionFlowService.advance(room.getId());
        assertThat(completed.status()).isEqualTo(SessionFlowResponse.FlowStatus.COMPLETED);
        assertThat(completed.currentStageId()).isNull();
        assertThat(completed.stages())
                .allMatch(stage -> stage.status() == com.precued.entity.RoomStage.Status.COMPLETED);
    }

    private TemplateRole role(Template template, String key, String name, boolean host, int sortOrder) {
        TemplateRole role = new TemplateRole();
        role.setTemplate(template);
        role.setRoleKey(key);
        role.setName(name);
        role.setHostRole(host);
        role.setGuestRole(false);
        role.setSortOrder(sortOrder);
        return role;
    }

    private List<Map<String, Object>> query(String sql) {
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        return jdbcTemplate.queryForList(sql);
    }

    private Map<String, List<String>> stageRoleKeys() {
        return query("""
                SELECT s.stage_key, r.role_key
                FROM template_stage_role sr
                JOIN template_stage s ON s.id = sr.template_stage_id
                JOIN template_role r ON r.id = sr.template_role_id
                WHERE s.template_id = 'mock_trial'
                ORDER BY s.sort_order, r.role_key
                """).stream().collect(Collectors.groupingBy(
                        row -> (String) row.get("STAGE_KEY"),
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(row -> (String) row.get("ROLE_KEY"), Collectors.toList())));
    }

    private String currentStageKey(SessionFlowResponse response) {
        return response.stages().stream()
                .filter(stage -> stage.id().equals(response.currentStageId()))
                .map(SessionFlowResponse.Stage::stageKey)
                .findFirst()
                .orElseThrow();
    }
}
