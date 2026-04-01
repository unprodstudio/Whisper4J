import dev.cadindie.whisper4j.Whisper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

public class FileTranscriptionTest {

    @Test
    public void testWAVTranscription() {
        System.setProperty("jna.nosys", "true");
        File model = new File(getClass().getResource("/models/ggml-base.bin").getFile());
        File sample = new File(getClass().getResource("/samples/sample.wav").getFile());
        Whisper whisper = new Whisper.Builder()
                .setModel(model)
                .setLanguage("en")
                .setTemperature(0.0f)
                .setUseGpu(true)
                .setDebugInfo(false)
                .build();
        whisper.initialize();
        try {
            String result = whisper.transcribeFile(sample);
            Assertions.assertEquals("None of those words are in the Bible.", result);
            Whisper.LOGGER.info("Transcription result: {}", result);
            whisper.close();
        } catch (Exception e) {
            Whisper.LOGGER.error(e.getMessage());
            whisper.close();
        }
    }
}
