import dev.cadindie.whisperapi.Whisper;
import dev.cadindie.whisperapi.audio.OpusTranscriber;
import net.labymod.opus.OpusCodec;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class OpusTranscriptionTest {
    private static final AudioFormat format =
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000, 16, 1, 2, 48000, false);

    @Test
    @Disabled
    public void testOpusTranscription() {
        System.setProperty("jna.nosys", "true");
        File model = new File(getClass().getResource("models/ggml-base.bin").getFile());
        Whisper whisper = new Whisper.Builder()
                .setModel(model)
                .setLanguage("en")
                .setTemperature(0.0f)
                .setUseGpu(true)
                .setDebugInfo(false)
                .build();
        whisper.initialize();
        try {
            opusTest(whisper);
            whisper.close();
        } catch (Exception e) {
            Whisper.LOGGER.error(e.getMessage());
            whisper.close();
        }
    }

    private void opusTest(Whisper whisper) throws IOException, LineUnavailableException {
        OpusTranscriber transcriber = new OpusTranscriber(5);

        OpusCodec.setupWithTemporaryFolder();
        OpusCodec codec = OpusCodec.createDefault();

        TargetDataLine microphone = AudioSystem.getTargetDataLine(format);
        microphone.open(microphone.getFormat());
        microphone.start();

        while (true) {
            //Reading microphone data
            byte[] data = new byte[codec.getChannels() * codec.getFrameSize() * 2];
            microphone.read(data, 0, data.length);

            //Encoding PCM data chunk
            byte[] encode = codec.encodeFrame(data);

            //Decoding PCM data chunk
            byte[] decoded = codec.decodeFrame(encode);

            String result = whisper.transcribeOpus(transcriber, decoded);
            if (!result.isBlank()) {
                Whisper.LOGGER.info("Transcription result: {}", result);
            }
        }
    }
}
