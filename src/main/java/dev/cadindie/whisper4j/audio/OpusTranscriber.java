package dev.cadindie.whisper4j.audio;

import dev.cadindie.whisper4j.Whisper;

import java.util.ArrayList;
import java.util.List;

public class OpusTranscriber {
    private int totalSamples = 0;

    private final double SAMPLES_PER_CHUNK;

    private final List<float[]> buffer = new ArrayList<>();

    public OpusTranscriber(double CHUNK_SECONDS) {
        int SAMPLE_RATE = 16000;
        int CHANNELS = 1;
        SAMPLES_PER_CHUNK = SAMPLE_RATE * CHUNK_SECONDS * CHANNELS;
    }

    public String transcribeOpusPacket(Whisper whisper, byte[] packet) {
        float[] samples = AudioLoader.pcm16AsByteToFloat(packet);

        buffer.add(samples);
        totalSamples += samples.length;

        if (totalSamples >= SAMPLES_PER_CHUNK) {
            try {
                String result = saveChunk(whisper);
                resetBuffer();
                return result;
            } catch (InterruptedException e) {
                resetBuffer();
                throw new RuntimeException(e);
            }
        }

        return "";
    }

    private String saveChunk(Whisper whisper) throws InterruptedException {
        float[] merged = new float[totalSamples];
        int offset = 0;

        for (float[] chunk : buffer) {
            System.arraycopy(chunk, 0, merged, offset, chunk.length);
            offset += chunk.length;
        }

        OpusTranscriptionThread ott = new OpusTranscriptionThread(whisper, merged);
        Thread thread = new Thread(ott);
        thread.start();
        thread.join();
        return ott.getTranscription();
    }

    private void resetBuffer() {
        buffer.clear();
        totalSamples = 0;
    }
}
