package com.interviewassistant.server.controller;

import com.interviewassistant.server.dto.AuthResponse;
import com.interviewassistant.server.dto.LoginRequest;
import com.interviewassistant.server.dto.RegisterRequest;
import com.interviewassistant.server.dto.SmsSendRequest;
import com.interviewassistant.server.dto.SmsVerifyRequest;
import com.interviewassistant.server.dto.UserProfileResponse;
import com.interviewassistant.server.service.AliyunSmsService;
import com.interviewassistant.server.service.CommercialFacadeService;
import com.interviewassistant.server.service.CurrentUserService;
import com.interviewassistant.server.service.SmsVerificationService;
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
    private final SmsVerificationService smsVerificationService;
    private final AliyunSmsService aliyunSmsService;

    public AuthController(CommercialFacadeService commercialFacadeService,
                          CurrentUserService currentUserService,
                          SmsVerificationService smsVerificationService,
                          AliyunSmsService aliyunSmsService) {
        this.commercialFacadeService = commercialFacadeService;
        this.currentUserService = currentUserService;
        this.smsVerificationService = smsVerificationService;
        this.aliyunSmsService = aliyunSmsService;
    }

    @PostMapping("/register/sms-code")
    public java.util.Map<String, Object> sendRegisterSmsCode(@Valid @RequestBody SmsSendRequest request) {
        String phone = request.getPhone().trim();
        String code = smsVerificationService.sendCode(phone);
        String debugCode = aliyunSmsService.sendRegisterCode(phone);
        return java.util.Map.of(
            "message", "验证码已发送",
            "debugCode", debugCode,
            "localCode", code
        );
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
