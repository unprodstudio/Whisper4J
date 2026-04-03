package dev.cadindie.whisper4j.threading;

import java.util.function.Consumer;

public record TranscriptionTask(float[] audio, Consumer<String> callback) {
}
