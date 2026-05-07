package com.interviewassistant.server.controller;

import com.interviewassistant.server.dto.AnalyzeRequest;
import com.interviewassistant.server.dto.AnalyzeResponse;
import com.interviewassistant.server.dto.AsrTokenResponse;
import com.interviewassistant.server.dto.HealthResponse;
import com.interviewassistant.server.service.AsrTokenService;
import com.interviewassistant.server.service.BailianAnswerService;
import com.interviewassistant.server.service.ClientAuthService;
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

    public ClientApiController(ClientAuthService clientAuthService,
                               AsrTokenService asrTokenService,
                               BailianAnswerService bailianAnswerService) {
        this.clientAuthService = clientAuthService;
        this.asrTokenService = asrTokenService;
        this.bailianAnswerService = bailianAnswerService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", "1.0.0");
    }

    @PostMapping("/asr/token")
    public AsrTokenResponse createAsrToken(@RequestHeader("X-Client-Secret") String clientSecret) throws Exception {
        clientAuthService.verify(clientSecret);
        return asrTokenService.createToken();
    }

    @PostMapping("/interview/analyze")
    public AnalyzeResponse analyze(@RequestHeader("X-Client-Secret") String clientSecret,
                                   @Valid @RequestBody AnalyzeRequest request) throws IOException {
        clientAuthService.verify(clientSecret);
        return bailianAnswerService.analyze(request);
    }
}
