package dev.cadindie.whisperapi.audio;

import dev.cadindie.whisperapi.Whisper;

public class OpusTranscriptionThread implements Runnable {
    Whisper whisper;
    float[] mergedSamples;
    String value;

    public OpusTranscriptionThread(Whisper whisper, float[] mergedSamples) {
        this.whisper = whisper;
        this.mergedSamples = mergedSamples;
    }

    @Override
    public void run() {
        value = whisper.transcribeRaw(mergedSamples);
    }

    public String getTranscription() {
        return value;
    }
}
