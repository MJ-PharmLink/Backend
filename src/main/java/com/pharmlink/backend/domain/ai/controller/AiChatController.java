package com.pharmlink.backend.domain.ai.controller;

import com.pharmlink.backend.domain.ai.dto.AiChatRequest;
import com.pharmlink.backend.domain.ai.dto.AiChatResponse;
import com.pharmlink.backend.domain.ai.service.AiChatService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PreAuthorize(AccessRole.ALL)
    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request
    ) {
        return ApiResponse.success(aiChatService.chat(request));
    }
}
