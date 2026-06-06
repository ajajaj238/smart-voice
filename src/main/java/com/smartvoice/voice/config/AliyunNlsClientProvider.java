package com.smartvoice.voice.config;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AliyunNlsClientProvider {

    private static final long TOKEN_REFRESH_WINDOW_SECONDS = 300;

    private final AliyunNlsProperties properties;
    private NlsClient client;
    private long tokenExpireTime;

    public synchronized NlsClient getClient() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Aliyun NLS is disabled.");
        }
        if (!properties.hasCredentials()) {
            throw new IllegalStateException("Missing Aliyun NLS credentials.");
        }
        long nowSeconds = System.currentTimeMillis() / 1000;
        if (client == null || nowSeconds + TOKEN_REFRESH_WINDOW_SECONDS >= tokenExpireTime) {
            refreshClient();
        }
        return client;
    }

    private void refreshClient() {
        shutdownClient();
        try {
            AccessToken accessToken = new AccessToken(
                    properties.getAccessKeyId(),
                    properties.getAccessKeySecret()
            );
            accessToken.apply();
            tokenExpireTime = accessToken.getExpireTime();
            client = new NlsClient(properties.getGatewayUrl(), accessToken.getToken());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Aliyun NLS client.", e);
        }
    }

    @PreDestroy
    public synchronized void shutdownClient() {
        if (client != null) {
            client.shutdown();
            client = null;
        }
    }
}
