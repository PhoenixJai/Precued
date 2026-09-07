package com.precued.controller;

import com.precued.controller.dto.AssignRoleRequest;
import com.precued.controller.dto.ParticipantRoleAssignmentResponse;
import com.precued.service.ParticipantRoleAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** TEMPORARY: no auth/session enforcement yet — see RoomController for the same caveat. */
@RestController
@RequestMapping("/api/participant-role-assignments")
public class ParticipantRoleAssignmentController {

    private final ParticipantRoleAssignmentService assignmentService;

    public ParticipantRoleAssignmentController(ParticipantRoleAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipantRoleAssignmentResponse assign(@Valid @RequestBody AssignRoleRequest request) {
        return ParticipantRoleAssignmentResponse.from(
                assignmentService.assign(request.roomParticipantId(), request.roomRoleId()));
    }

    @PostMapping("/{id}/revoke")
    public ParticipantRoleAssignmentResponse revoke(@PathVariable UUID id) {
        return ParticipantRoleAssignmentResponse.from(assignmentService.revoke(id));
    }
}
