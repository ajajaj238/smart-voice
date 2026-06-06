package com.smartvoice.voice.asr;

import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechRecognizer;
import com.alibaba.nls.client.protocol.asr.SpeechRecognizerListener;
import com.alibaba.nls.client.protocol.asr.SpeechRecognizerResponse;
import com.smartvoice.voice.config.AliyunNlsClientProvider;
import com.smartvoice.voice.config.AliyunNlsProperties;
import com.smartvoice.voice.dto.AsrResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsrService {

    private static final int AUDIO_CHUNK_SIZE = 3200;

    private final AliyunNlsClientProvider nlsClientProvider;
    private final AliyunNlsProperties properties;

    public AsrResponse transcribe(MultipartFile audio, String transcriptHint, String language, Integer durationMs) {
        if (audio != null && !audio.isEmpty() && properties.isEnabled()) {
            if (!properties.hasCredentials()) {
                if (!properties.isFallbackEnabled()) {
                    throw new IllegalStateException("Missing Aliyun NLS credentials: " + properties.missingCredentialNames());
                }
                log.warn("Missing Aliyun NLS credentials: {}, fallback to transcriptHint. filename={}",
                        properties.missingCredentialNames(), audio.getOriginalFilename());
                return fallbackTranscribe(audio, transcriptHint, language, durationMs);
            }
            try {
                return transcribeWithAliyun(audio, language, durationMs);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                if (!properties.isFallbackEnabled()) {
                    throw new IllegalStateException("Aliyun NLS ASR failed.", e);
                }
                log.warn("Aliyun NLS ASR failed, fallback to transcriptHint. filename={}",
                        audio.getOriginalFilename(), e);
            }
        }

        if ((audio == null || audio.isEmpty()) && !properties.isFallbackEnabled() && !StringUtils.hasText(transcriptHint)) {
            throw new IllegalStateException("Audio is required when Aliyun NLS fallback is disabled.");
        }
        return fallbackTranscribe(audio, transcriptHint, language, durationMs);
    }

    private AsrResponse transcribeWithAliyun(MultipartFile audio, String language, Integer durationMs) throws Exception {
        AudioPayload payload = prepareAudioPayload(audio, durationMs);
        AtomicReference<String> recognizedText = new AtomicReference<>("");
        AtomicReference<SpeechRecognizerResponse> completedResponse = new AtomicReference<>();
        AtomicReference<SpeechRecognizerResponse> failedResponse = new AtomicReference<>();

        SpeechRecognizerListener listener = new SpeechRecognizerListener() {
            @Override
            public void onRecognitionResultChanged(SpeechRecognizerResponse response) {
                if (StringUtils.hasText(response.getRecognizedText())) {
                    recognizedText.set(response.getRecognizedText());
                }
            }

            @Override
            public void onRecognitionCompleted(SpeechRecognizerResponse response) {
                completedResponse.set(response);
                if (StringUtils.hasText(response.getRecognizedText())) {
                    recognizedText.set(response.getRecognizedText());
                }
                log.info("Aliyun ASR completed, taskId={}, status={}, text={}",
                        response.getTaskId(), response.getStatus(), response.getRecognizedText());
            }

            @Override
            public void onStarted(SpeechRecognizerResponse response) {
                log.info("Aliyun ASR started, taskId={}", response.getTaskId());
            }

            @Override
            public void onFail(SpeechRecognizerResponse response) {
                failedResponse.set(response);
                log.warn("Aliyun ASR failed, taskId={}, status={}, statusText={}",
                        response.getTaskId(), response.getStatus(), response.getStatusText());
            }
        };

        SpeechRecognizer recognizer = null;
        try {
            recognizer = new SpeechRecognizer(nlsClientProvider.getClient(), listener);
            recognizer.setAppKey(properties.getAppKey());
            recognizer.setFormat(payload.format());
            recognizer.setSampleRate(resolveSampleRate(payload.sampleRate()));
            recognizer.setEnableIntermediateResult(true);
            recognizer.addCustomedParam("enable_voice_detection", true);
            recognizer.start();

            byte[] bytes = payload.bytes();
            for (int offset = 0; offset < bytes.length; offset += AUDIO_CHUNK_SIZE) {
                int len = Math.min(AUDIO_CHUNK_SIZE, bytes.length - offset);
                recognizer.send(Arrays.copyOfRange(bytes, offset, offset + len), len);
                Thread.sleep(getSleepDelta(len, payload.sampleRate()));
            }

            recognizer.stop();
            SpeechRecognizerResponse fail = failedResponse.get();
            if (fail != null) {
                throw new IllegalStateException("Aliyun ASR failed: " + fail.getStatusText());
            }
            String text = recognizedText.get();
            if (!StringUtils.hasText(text)) {
                throw new IllegalStateException("Aliyun ASR returned empty text. Please check audio format: use 16kHz/8kHz mono 16-bit PCM or WAV with clear speech.");
            }
            SpeechRecognizerResponse complete = completedResponse.get();
            return new AsrResponse(
                    text,
                    defaultLanguage(language),
                    StringUtils.hasText(text) ? 0.95 : 0.0,
                    payload.durationMs(),
                    complete != null
            );
        } finally {
            if (recognizer != null) {
                recognizer.close();
            }
        }
    }

    private AsrResponse fallbackTranscribe(MultipartFile audio, String transcriptHint, String language, Integer durationMs) {
        String text = transcriptHint == null ? "" : transcriptHint.trim();
        Integer resolvedDurationMs = resolveDurationMs(audio, durationMs);
        double confidence = 0.92;
        if (text.isBlank()) {
            text = "Hi, I would like to practice this conversation.";
            confidence = audio == null || audio.isEmpty() ? 0.35 : 0.68;
        }
        return new AsrResponse(
                text,
                defaultLanguage(language),
                confidence,
                resolvedDurationMs,
                true
        );
    }

    private Integer resolveDurationMs(MultipartFile audio, Integer durationMs) {
        if (durationMs != null && durationMs > 0) {
            return durationMs;
        }
        if (audio == null || audio.isEmpty()) {
            return 2500;
        }
        long estimated = Math.max(1000, Math.min(12000, audio.getSize() / 32));
        return (int) estimated;
    }

    private AudioPayload prepareAudioPayload(MultipartFile audio, Integer durationMs) throws Exception {
        byte[] bytes = audio.getBytes();
        String filename = audio.getOriginalFilename();
        String contentType = audio.getContentType();
        String marker = ((filename == null ? "" : filename) + " " + (contentType == null ? "" : contentType))
                .toLowerCase();

        if (looksLikeWav(marker) && !hasRiffWaveHeader(bytes)) {
            throw new IllegalArgumentException("The uploaded file is marked as WAV, but it does not contain a valid RIFF/WAVE header. Please convert it to 16kHz/8kHz mono 16-bit PCM WAV.");
        }
        if (hasRiffWaveHeader(bytes)) {
            return parseWavPayload(bytes, durationMs);
        }
        if (marker.contains("opus")) {
            return new AudioPayload(bytes, InputFormatEnum.OPUS, properties.getSampleRate(), resolveDurationMs(audio, durationMs));
        }
        if (isCompressedAudio(marker)) {
            throw new IllegalArgumentException("Unsupported ASR audio format. Please upload 16kHz/8kHz mono 16-bit PCM or WAV. MP3/M4A/AAC/WebM must be converted before upload.");
        }

        int sampleRate = properties.getSampleRate();
        validateSupportedSampleRate(sampleRate);
        log.info("Aliyun ASR audio prepared, filename={}, contentType={}, format=PCM, sampleRate={}, bytes={}",
                filename, contentType, sampleRate, bytes.length);
        return new AudioPayload(bytes, InputFormatEnum.PCM, sampleRate, resolveDurationMs(audio, durationMs));
    }

    private AudioPayload parseWavPayload(byte[] wavBytes, Integer durationMs) {
        if (wavBytes.length < 44) {
            throw new IllegalArgumentException("Invalid WAV file: file is too small.");
        }

        int offset = 12;
        Integer audioFormat = null;
        Integer channels = null;
        Integer sampleRate = null;
        Integer bitsPerSample = null;
        byte[] pcmBytes = null;

        while (offset + 8 <= wavBytes.length) {
            String chunkId = ascii(wavBytes, offset, 4);
            long chunkSize = littleEndianUnsignedInt(wavBytes, offset + 4);
            int chunkDataStart = offset + 8;
            if (chunkDataStart > wavBytes.length) {
                throw new IllegalArgumentException("Invalid WAV file: chunk data starts beyond file length.");
            }
            long remaining = wavBytes.length - (long) chunkDataStart;
            if (chunkSize > remaining) {
                if ("data".equals(chunkId)) {
                    log.warn("WAV data chunk size exceeds file length, using remaining bytes. declaredSize={}, remaining={}",
                            chunkSize, remaining);
                    chunkSize = remaining;
                } else {
                    log.warn("WAV chunk size exceeds file length, stop parsing. chunkId={}, declaredSize={}, remaining={}",
                            chunkId, chunkSize, remaining);
                    break;
                }
            }

            if ("fmt ".equals(chunkId)) {
                audioFormat = littleEndianShort(wavBytes, chunkDataStart);
                channels = littleEndianShort(wavBytes, chunkDataStart + 2);
                sampleRate = littleEndianInt(wavBytes, chunkDataStart + 4);
                bitsPerSample = littleEndianShort(wavBytes, chunkDataStart + 14);
            } else if ("data".equals(chunkId)) {
                pcmBytes = Arrays.copyOfRange(wavBytes, chunkDataStart, (int) (chunkDataStart + chunkSize));
            }

            offset = (int) (chunkDataStart + chunkSize + (chunkSize % 2));
        }

        if (audioFormat == null || channels == null || sampleRate == null || bitsPerSample == null || pcmBytes == null) {
            throw new IllegalArgumentException("Invalid WAV file: missing fmt or data chunk.");
        }
        if (audioFormat != 1) {
            throw new IllegalArgumentException("Unsupported WAV encoding. Please use PCM WAV, not compressed WAV.");
        }
        if (bitsPerSample != 16) {
            throw new IllegalArgumentException("Unsupported WAV bit depth. Please use 16-bit PCM WAV.");
        }
        validateSupportedSampleRate(sampleRate);

        if (channels != 1) {
            pcmBytes = downmixToMonoPcm16(pcmBytes, channels);
        }

        int resolvedDurationMs = durationMs != null && durationMs > 0
                ? durationMs
                : (int) Math.max(1, pcmBytes.length * 1000L / (sampleRate * 2L));
        log.info("Aliyun ASR WAV prepared, sampleRate={}, channels={}, bitsPerSample={}, monoPcmBytes={}, durationMs={}",
                sampleRate, channels, bitsPerSample, pcmBytes.length, resolvedDurationMs);
        return new AudioPayload(pcmBytes, InputFormatEnum.PCM, sampleRate, resolvedDurationMs);
    }

    private boolean looksLikeWav(String marker) {
        return marker.contains(".wav")
                || marker.contains("audio/wav")
                || marker.contains("audio/x-wav");
    }

    private boolean isCompressedAudio(String marker) {
        return marker.contains(".mp3")
                || marker.contains("mpeg")
                || marker.contains(".m4a")
                || marker.contains("mp4")
                || marker.contains(".aac")
                || marker.contains("aac")
                || marker.contains(".webm")
                || marker.contains("webm");
    }

    private boolean hasRiffWaveHeader(byte[] bytes) {
        return bytes.length >= 12
                && "RIFF".equals(ascii(bytes, 0, 4))
                && "WAVE".equals(ascii(bytes, 8, 4));
    }

    private SampleRateEnum resolveSampleRate(int sampleRate) {
        if (sampleRate == 8000) {
            return SampleRateEnum.SAMPLE_RATE_8K;
        }
        return SampleRateEnum.SAMPLE_RATE_16K;
    }

    private void validateSupportedSampleRate(int sampleRate) {
        if (sampleRate != 8000 && sampleRate != 16000) {
            throw new IllegalArgumentException("Unsupported sample rate: " + sampleRate + ". Please use 8000Hz or 16000Hz audio.");
        }
    }

    private int getSleepDelta(int dataSize, int sampleRate) {
        return Math.max(1, (dataSize * 10 * 8000) / (160 * sampleRate));
    }

    private String defaultLanguage(String language) {
        return language == null || language.isBlank() ? "en-US" : language;
    }

    private String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private int littleEndianInt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private long littleEndianUnsignedInt(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(littleEndianInt(bytes, offset));
    }

    private int littleEndianShort(byte[] bytes, int offset) {
        return Short.toUnsignedInt(ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
    }

    private byte[] downmixToMonoPcm16(byte[] pcmBytes, int channels) {
        if (channels <= 1) {
            return pcmBytes;
        }

        int frameSize = channels * 2;
        if (pcmBytes.length % frameSize != 0) {
            throw new IllegalArgumentException("Invalid WAV PCM data: frame size does not match channel count.");
        }

        byte[] monoBytes = new byte[(pcmBytes.length / channels)];
        int monoOffset = 0;
        for (int offset = 0; offset < pcmBytes.length; offset += frameSize) {
            int mixedSample = 0;
            for (int channel = 0; channel < channels; channel++) {
                int sampleOffset = offset + channel * 2;
                short sample = ByteBuffer.wrap(pcmBytes, sampleOffset, 2)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getShort();
                mixedSample += sample;
            }
            short monoSample = (short) (mixedSample / channels);
            monoBytes[monoOffset++] = (byte) (monoSample & 0xff);
            monoBytes[monoOffset++] = (byte) ((monoSample >> 8) & 0xff);
        }
        return monoBytes;
    }

    private record AudioPayload(
            byte[] bytes,
            InputFormatEnum format,
            int sampleRate,
            Integer durationMs
    ) {
    }
}
