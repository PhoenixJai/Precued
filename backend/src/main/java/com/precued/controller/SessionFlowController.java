package com.precued.controller;

import com.precued.controller.dto.SessionFlowResponse;
import com.precued.service.SessionFlowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/session-flow")
public class SessionFlowController {

    private final SessionFlowService sessionFlowService;

    public SessionFlowController(SessionFlowService sessionFlowService) {
        this.sessionFlowService = sessionFlowService;
    }

    @GetMapping
    public SessionFlowResponse get(@PathVariable UUID roomId) {
        return sessionFlowService.get(roomId);
    }

    @PostMapping("/start")
    public SessionFlowResponse start(@PathVariable UUID roomId) {
        return sessionFlowService.start(roomId);
    }

    @PostMapping("/advance")
    public SessionFlowResponse advance(@PathVariable UUID roomId) {
        return sessionFlowService.advance(roomId);
    }
}
