package com.interviewassistant.server.controller;

import com.interviewassistant.server.dto.AnalyzeRequest;
import com.interviewassistant.server.dto.AnalyzeResponse;
import com.interviewassistant.server.dto.AsrTokenResponse;
import com.interviewassistant.server.dto.HealthResponse;
import com.interviewassistant.server.service.AsrTokenService;
import com.interviewassistant.server.service.BailianAnswerService;
import com.interviewassistant.server.service.ClientAuthService;
import com.interviewassistant.server.service.CurrentUserService;
import com.interviewassistant.server.service.CommercialFacadeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/client")
public class ClientApiController {
    private final ClientAuthService clientAuthService;
    private final AsrTokenService asrTokenService;
    private final BailianAnswerService bailianAnswerService;
    private final CommercialFacadeService commercialFacadeService;
    private final CurrentUserService currentUserService;

    public ClientApiController(ClientAuthService clientAuthService,
                               AsrTokenService asrTokenService,
                               BailianAnswerService bailianAnswerService,
                               CommercialFacadeService commercialFacadeService,
                               CurrentUserService currentUserService) {
        this.clientAuthService = clientAuthService;
        this.asrTokenService = asrTokenService;
        this.bailianAnswerService = bailianAnswerService;
        this.commercialFacadeService = commercialFacadeService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", "1.0.0");
    }

    @PostMapping("/asr/token")
    public AsrTokenResponse createAsrToken(@RequestHeader(value = "X-Client-Secret", required = false) String clientSecret) throws Exception {
        validateLegacySecretIfPresent(clientSecret);
        commercialFacadeService.ensureUserCanUseCoreFeature(currentUserService.requireCurrentUserId());
        return asrTokenService.createToken();
    }

    @PostMapping("/interview/analyze")
    public AnalyzeResponse analyze(@RequestHeader(value = "X-Client-Secret", required = false) String clientSecret,
                                   @Valid @RequestBody AnalyzeRequest request) throws IOException {
        validateLegacySecretIfPresent(clientSecret);
        commercialFacadeService.ensureUserCanUseCoreFeature(currentUserService.requireCurrentUserId());
        return bailianAnswerService.analyze(request);
    }

    private void validateLegacySecretIfPresent(String clientSecret) {
        if (clientSecret != null && !clientSecret.isBlank()) {
            clientAuthService.verify(clientSecret);
        }
    }
}
