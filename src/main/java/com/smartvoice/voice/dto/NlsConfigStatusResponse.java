package com.smartvoice.voice.dto;

public record NlsConfigStatusResponse(
        boolean enabled,
        boolean fallbackEnabled,
        boolean appKeySet,
        boolean accessKeyIdSet,
        boolean accessKeySecretSet,
        String missingCredentials,
        String gatewayUrl,
        int sampleRate,
        String ttsVoice,
        String ttsFormat
) {
}
