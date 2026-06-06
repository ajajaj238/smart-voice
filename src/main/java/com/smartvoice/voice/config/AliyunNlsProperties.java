package com.smartvoice.voice.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.aliyun.nls")
public class AliyunNlsProperties {

    private boolean enabled = true;
    private boolean fallbackEnabled = false;
    private String appKey;
    private String accessKeyId;
    private String accessKeySecret;
    private String gatewayUrl = "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1";
    private int sampleRate = 16000;
    private String ttsVoice = "zhixiaobai";
    private String ttsFormat = "wav";
    private int ttsVolume = 50;
    private int ttsSpeechRate = 0;
    private int ttsPitchRate = 0;

    public boolean hasCredentials() {
        return StringUtils.hasText(appKey)
                && StringUtils.hasText(accessKeyId)
                && StringUtils.hasText(accessKeySecret);
    }

    public String missingCredentialNames() {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(appKey)) {
            missing.add("NLS_APP_KEY");
        }
        if (!StringUtils.hasText(accessKeyId)) {
            missing.add("ALIYUN_AK_ID");
        }
        if (!StringUtils.hasText(accessKeySecret)) {
            missing.add("ALIYUN_AK_SECRET");
        }
        return String.join(", ", missing);
    }

    @PostConstruct
    public void logConfigStatus() {
        log.info("Aliyun NLS config loaded: enabled={}, fallbackEnabled={}, appKeySet={}, accessKeyIdSet={}, accessKeySecretSet={}, gatewayUrl={}, sampleRate={}, ttsVoice={}, ttsFormat={}",
                enabled,
                fallbackEnabled,
                StringUtils.hasText(appKey),
                StringUtils.hasText(accessKeyId),
                StringUtils.hasText(accessKeySecret),
                gatewayUrl,
                sampleRate,
                ttsVoice,
                ttsFormat);
    }
}
