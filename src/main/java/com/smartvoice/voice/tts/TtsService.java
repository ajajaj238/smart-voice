package com.smartvoice.voice.tts;

import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import com.smartvoice.voice.config.AliyunNlsClientProvider;
import com.smartvoice.voice.config.AliyunNlsProperties;
import com.smartvoice.voice.dto.TtsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private static final int SAMPLE_RATE = 16_000;
    private static final int BYTES_PER_SAMPLE = 2;

    private final AliyunNlsClientProvider nlsClientProvider;
    private final AliyunNlsProperties properties;

    public TtsResponse synthesize(String text, String voice, String format) {
        if (properties.isEnabled()) {
            if (!properties.hasCredentials()) {
                if (!properties.isFallbackEnabled()) {
                    throw new IllegalStateException("Missing Aliyun NLS credentials: " + properties.missingCredentialNames());
                }
                log.warn("Missing Aliyun NLS credentials: {}, fallback to silent wav.", properties.missingCredentialNames());
                return fallbackSynthesize(text, voice, format);
            }
            try {
                return synthesizeWithAliyun(text, voice, format);
            } catch (Exception e) {
                if (!properties.isFallbackEnabled()) {
                    throw new IllegalStateException("Aliyun NLS TTS failed.", e);
                }
                log.warn("Aliyun NLS TTS failed, fallback to silent wav. text={}", text, e);
            }
        }

        if (!properties.isFallbackEnabled()) {
            throw new IllegalStateException("Aliyun NLS is disabled and fallback is disabled.");
        }
        log.warn("Aliyun NLS is disabled, fallback to silent wav.");
        return fallbackSynthesize(text, voice, format);
    }

    private TtsResponse synthesizeWithAliyun(String text, String voice, String format) throws Exception {
        String safeText = text == null ? "" : text.trim();
        String resolvedVoice = voice == null || voice.isBlank() ? properties.getTtsVoice() : voice;
        String resolvedFormat = format == null || format.isBlank() ? properties.getTtsFormat() : format;
        if (safeText.length() > 300) {
            safeText = safeText.substring(0, 300);
        }

        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        SpeechSynthesizerListener listener = new SpeechSynthesizerListener() {
            @Override
            public void onMessage(ByteBuffer message) {
                byte[] bytes = new byte[message.remaining()];
                message.get(bytes);
                audio.writeBytes(bytes);
            }

            @Override
            public void onComplete(SpeechSynthesizerResponse response) {
                log.info("Aliyun TTS completed, taskId={}, status={}",
                        response.getTaskId(), response.getStatus());
            }

            @Override
            public void onFail(SpeechSynthesizerResponse response) {
                log.warn("Aliyun TTS failed, taskId={}, status={}, statusText={}",
                        response.getTaskId(), response.getStatus(), response.getStatusText());
            }
        };

        SpeechSynthesizer synthesizer = null;
        try {
            synthesizer = new SpeechSynthesizer(nlsClientProvider.getClient(), listener);
            synthesizer.setAppKey(properties.getAppKey());
            synthesizer.setText(safeText);
            synthesizer.setVoice(resolvedVoice);
            synthesizer.setFormat(resolveOutputFormat(resolvedFormat));
            synthesizer.setSampleRate(resolveSampleRate());
            synthesizer.setVolume(properties.getTtsVolume());
            synthesizer.setSpeechRate(properties.getTtsSpeechRate());
            synthesizer.setPitchRate(properties.getTtsPitchRate());
            synthesizer.start();
            synthesizer.waitForComplete(10000);

            byte[] bytes = audio.toByteArray();
            if (bytes.length == 0) {
                throw new IllegalStateException("Aliyun NLS TTS returned empty audio.");
            }

            return new TtsResponse(
                    safeText,
                    resolvedVoice,
                    resolvedFormat,
                    mimeType(resolvedFormat),
                    estimateDurationMs(safeText),
                    Base64.getEncoder().encodeToString(bytes)
            );
        } finally {
            if (synthesizer != null) {
                synthesizer.close();
            }
        }
    }

    private TtsResponse fallbackSynthesize(String text, String voice, String format) {
        String safeText = text == null ? "" : text.trim();
        String resolvedVoice = voice == null || voice.isBlank() ? properties.getTtsVoice() : voice;
        String resolvedFormat = format == null || format.isBlank() ? properties.getTtsFormat() : format;
        int durationMs = estimateDurationMs(safeText);
        byte[] audio = "wav".equalsIgnoreCase(resolvedFormat)
                ? silentWav(durationMs)
                : safeText.getBytes(StandardCharsets.UTF_8);

        return new TtsResponse(
                safeText,
                resolvedVoice,
                resolvedFormat,
                "wav".equalsIgnoreCase(resolvedFormat) ? "audio/wav" : "text/plain",
                durationMs,
                Base64.getEncoder().encodeToString(audio)
        );
    }

    private OutputFormatEnum resolveOutputFormat(String format) {
        if ("mp3".equalsIgnoreCase(format)) {
            return OutputFormatEnum.MP3;
        }
        if ("pcm".equalsIgnoreCase(format)) {
            return OutputFormatEnum.PCM;
        }
        return OutputFormatEnum.WAV;
    }

    private SampleRateEnum resolveSampleRate() {
        if (properties.getSampleRate() == 8000) {
            return SampleRateEnum.SAMPLE_RATE_8K;
        }
        return SampleRateEnum.SAMPLE_RATE_16K;
    }

    private String mimeType(String format) {
        return switch (format.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "pcm" -> "audio/pcm";
            default -> "audio/wav";
        };
    }

    private int estimateDurationMs(String text) {
        int wordCount = text.isBlank() ? 1 : text.split("\\s+").length;
        return Math.max(800, Math.min(7000, 500 + wordCount * 230));
    }

    private byte[] silentWav(int durationMs) {
        int sampleCount = SAMPLE_RATE * durationMs / 1000;
        int dataSize = sampleCount * BYTES_PER_SAMPLE;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize);

        writeAscii(out, "RIFF");
        writeLittleEndianInt(out, 36 + dataSize);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeLittleEndianInt(out, 16);
        writeLittleEndianShort(out, 1);
        writeLittleEndianShort(out, 1);
        writeLittleEndianInt(out, SAMPLE_RATE);
        writeLittleEndianInt(out, SAMPLE_RATE * BYTES_PER_SAMPLE);
        writeLittleEndianShort(out, BYTES_PER_SAMPLE);
        writeLittleEndianShort(out, 16);
        writeAscii(out, "data");
        writeLittleEndianInt(out, dataSize);
        out.writeBytes(new byte[dataSize]);

        return out.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeLittleEndianInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private void writeLittleEndianShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }
}
