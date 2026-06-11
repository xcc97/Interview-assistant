package com.interviewassistant.server.service.impl;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.interviewassistant.server.config.AssistantProperties;
import com.interviewassistant.server.service.AliyunSmsService;
import org.springframework.stereotype.Service;

@Service
public class AliyunSmsServiceImpl implements AliyunSmsService {
    private static final String ENDPOINT = "dysmsapi.aliyuncs.com";
    private static final String SUCCESS_CODE = "OK";

    private final AssistantProperties assistantProperties;

    public AliyunSmsServiceImpl(AssistantProperties assistantProperties) {
        this.assistantProperties = assistantProperties;
    }

    @Override
    public void sendRegisterCode(String phone, String code) {
        if (!isConfigured()) {
            return;
        }
        try {
            AssistantProperties.Aliyun aliyun = assistantProperties.getAliyun();
            Client client = createClient(aliyun);
            SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(aliyun.getSmsSignName())
                .setTemplateCode(aliyun.getSmsRegisterTemplateCode())
                .setTemplateParam("{\"code\":\"" + code + "\"}");
            SendSmsResponse response = client.sendSms(request);
            String responseCode = response.getBody() == null ? null : response.getBody().getCode();
            if (!SUCCESS_CODE.equals(responseCode)) {
                String message = response.getBody() == null ? "短信发送失败" : response.getBody().getMessage();
                throw new IllegalStateException("短信发送失败：" + message);
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("短信发送失败，请稍后重试", exception);
        }
    }

    @Override
    public boolean isConfigured() {
        AssistantProperties.Aliyun aliyun = assistantProperties.getAliyun();
        return !aliyun.getAccessKeyId().isBlank()
            && !aliyun.getAccessKeySecret().isBlank()
            && !aliyun.getSmsSignName().isBlank()
            && !aliyun.getSmsRegisterTemplateCode().isBlank();
    }

    @Override
    public boolean isDebugEnabled() {
        return assistantProperties.getAliyun().isSmsDebugEnabled();
    }

    private Client createClient(AssistantProperties.Aliyun aliyun) throws Exception {
        Config config = new Config()
            .setAccessKeyId(aliyun.getAccessKeyId())
            .setAccessKeySecret(aliyun.getAccessKeySecret())
            .setEndpoint(ENDPOINT);
        return new Client(config);
    }
}
