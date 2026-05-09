package com.interviewassistant.server.controller;

import com.interviewassistant.server.dto.AuthResponse;
import com.interviewassistant.server.dto.LoginRequest;
import com.interviewassistant.server.dto.RegisterRequest;
import com.interviewassistant.server.dto.UserProfileResponse;
import com.interviewassistant.server.service.CommercialFacadeService;
import com.interviewassistant.server.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CommercialFacadeService commercialFacadeService;
    private final CurrentUserService currentUserService;

    public AuthController(CommercialFacadeService commercialFacadeService,
                          CurrentUserService currentUserService) {
        this.commercialFacadeService = commercialFacadeService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return commercialFacadeService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return commercialFacadeService.login(request);
    }

    @GetMapping("/me")
    public UserProfileResponse me() {
        return commercialFacadeService.getProfile(currentUserService.requireCurrentUserId());
    }
}
