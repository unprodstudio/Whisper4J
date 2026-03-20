package dev.cadindie.whisperapi;

import dev.cadindie.whisperapi.audio.AudioLoader;
import dev.cadindie.whisperapi.audio.OpusTranscriber;
import io.github.ggerganov.whispercpp.WhisperCpp;
import io.github.ggerganov.whispercpp.WhisperCppJnaLibrary;
import io.github.ggerganov.whispercpp.params.WhisperFullParams;
import io.github.ggerganov.whispercpp.params.WhisperSamplingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Whisper {
    private final WhisperCpp whisperCpp;
    private final File model;
    private final WhisperFullParams.ByValue whisperParams;
    public static final Logger LOGGER = LoggerFactory.getLogger(Whisper.class);

    public Whisper(WhisperCpp whisperCpp, File model, WhisperFullParams.ByValue whisperParams) {
        this.whisperCpp = whisperCpp;
        this.model = model;
        this.whisperParams = whisperParams;
    }

    public void initialize() {
        try {
            whisperCpp.initContext(model.toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to init WhisperCPP due to: " + e);
        }
    }

    /**
     * Transcribes raw audio data represented as an array of floats. Each float should be in the range [-1.0, 1.0], representing the normalized audio sample values.
     *
     * @param samples The raw audio data to transcribe.
     * @return The transcription result in string form.
     */
    public String transcribeRaw(float[] samples) {
        try {
            return whisperCpp.fullTranscribe(whisperParams, samples);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Transcribes audio directly from an audio file (e.g. WAV, AIFF, MP3).
     *
     * @param file The audio file to transcribe.
     * @return The transcription result in string form.
     */
    public String transcribeFile(File file) {
         try {
             float[] samples = AudioLoader.load(file.toPath());
             return whisperCpp.fullTranscribe(whisperParams, samples);
         } catch(Exception e) {
             throw new RuntimeException(e);
         }
    }

    /**
     * Transcribes audio from an Opus encoded packet.
     *
     * @param packet The opus packet as a byte array to transcribe.
     * @return The transcription result in string form.
     */
    public String transcribeOpus(OpusTranscriber transcriber, byte[] packet) {
        return transcriber.transcribeOpusPacket(this, packet);
    }

    public void close() {
        if (whisperCpp != null) {
            whisperCpp.close();
        } else {
            throw new IllegalStateException("WhisperCPP context is not initialized.");
        }
    }

    public static class Builder {
        private final WhisperCpp whisperCpp = new WhisperCpp();
        private final WhisperFullParams.ByValue whisperParams = whisperCpp.getFullDefaultParams(WhisperSamplingStrategy.WHISPER_SAMPLING_BEAM_SEARCH);
        private File model;
        private boolean useGpu;

        public Builder setModel(File model) {
            this.model = model;
            return this;
        }

        public Builder setLanguage(String language) {
            whisperParams.language = language;
            return this;
        }

        public Builder setUseGpu(boolean useGpu) {
            this.useGpu = useGpu;
            return this;
        }

        public Builder setTemperature(float temperature) {
            whisperParams.temperature = temperature;
            return this;
        }

        public Builder setDebugInfo(boolean enableDebug) {
            whisperParams.enableDebugMode(enableDebug);
            whisperParams.printProgress(enableDebug);
            whisperParams.printRealtime(enableDebug);
            whisperParams.printSpecial(enableDebug);
            whisperParams.printTimestamps(enableDebug);
            return this;
        }

        public Whisper build() {
            return new Whisper(whisperCpp, model, whisperParams);
        }
    }
}
