package dev.cadindie.whisperapi.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Loads an audio file and converts it to the PCM format required by whisper.cpp:
 * 16 kHz, mono, 32-bit float, samples in the range [-1.0, 1.0].
 *
 * <p>Any format that Java's {@link AudioSystem} can decode (e.g. WAV, AIFF) is
 * accepted, regardless of sample rate, bit depth, or channel count.  For
 * compressed formats such as MP3 or FLAC, install an appropriate SPI on the
 * class-path (e.g. {@code mp3spi}, {@code jflac}) or convert the file to WAV
 * beforehand with {@code ffmpeg -i input.mp3 output.wav}.
 */
public final class AudioLoader {

    /** Sample rate required by whisper.cpp. */
    public static final int WHISPER_SAMPLE_RATE = 16_000;

    private AudioLoader() {}

    /**
     * Loads the audio at {@code path} and returns 16 kHz mono float32 samples.
     *
     * <p>The conversion pipeline is:
     * <ol>
     *   <li>Decode any non-PCM_SIGNED encoding (alaw, ulaw, unsigned PCM, …) to
     *       PCM_SIGNED while preserving the source sample rate, channel count, and
     *       bit depth.</li>
     *   <li>Convert the raw PCM bytes to float32, mixing interleaved channels down
     *       to mono by averaging.</li>
     *   <li>Resample from the source sample rate to {@value #WHISPER_SAMPLE_RATE} Hz
     *       using linear interpolation when the rates differ.</li>
     * </ol>
     *
     * @param path audio file (WAV, AIFF, or any registered SPI format)
     * @return float[] with samples in [-1.0, 1.0]
     * @throws UnsupportedAudioFileException if the format cannot be decoded
     * @throws IOException                   on I/O error
     */
    public static float[] load(Path path) throws UnsupportedAudioFileException, IOException {
        try (AudioInputStream rawStream = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat src = rawStream.getFormat();

            // Step 1: Normalise to PCM_SIGNED (same sample rate / channels / bit depth).
            // This single AudioSystem call handles alaw, ulaw, unsigned PCM, and
            // big-endian variants – all conversions that Java's built-in converters
            // support reliably without needing to change sample rate or channel count.
            try (AudioInputStream pcmStream = normalizeToPcmSigned(rawStream, src)) {
                AudioFormat fmt = pcmStream.getFormat();
                byte[] bytes = pcmStream.readAllBytes();

                int bits = fmt.getSampleSizeInBits() > 0 ? fmt.getSampleSizeInBits() : 16;
                boolean bigEndian = fmt.isBigEndian();
                int channels = fmt.getChannels() > 0 ? fmt.getChannels() : 1;
                float sampleRate = fmt.getSampleRate() > 0 ? fmt.getSampleRate() : WHISPER_SAMPLE_RATE;

                // Step 2: Raw PCM bytes → mono float32 (channel mix-down included).
                float[] mono = rawPcmToMono(bytes, bits, bigEndian, channels);

                // Step 3: Resample to WHISPER_SAMPLE_RATE when the source rate differs.
                return Math.round(sampleRate) == WHISPER_SAMPLE_RATE
                        ? mono
                        : resample(mono, sampleRate, WHISPER_SAMPLE_RATE);
            }
        }
    }

    /**
     * Returns an {@link AudioInputStream} in PCM_SIGNED encoding with the same
     * sample rate, channel count, and bit depth as the source.  If the source is
     * already PCM_SIGNED the original stream is returned unchanged.
     */
    private static AudioInputStream normalizeToPcmSigned(AudioInputStream stream,
                                                         AudioFormat src) {
        if (AudioFormat.Encoding.PCM_SIGNED.equals(src.getEncoding())) {
            return stream;
        }

        float rate = src.getSampleRate() > 0 ? src.getSampleRate() : WHISPER_SAMPLE_RATE;
        int channels = src.getChannels() > 0 ? src.getChannels() : 1;

        // For raw PCM variants (unsigned, float) preserve the source bit depth and
        // byte order.  For compressed/encoded formats (alaw, ulaw, …) decode to
        // 16-bit little-endian because the decoded bit depth may differ.
        boolean isRawPcm = AudioFormat.Encoding.PCM_UNSIGNED.equals(src.getEncoding())
                || AudioFormat.Encoding.PCM_FLOAT.equals(src.getEncoding());
        int bits = isRawPcm && src.getSampleSizeInBits() > 0 ? src.getSampleSizeInBits() : 16;
        boolean bigEndian = isRawPcm && src.isBigEndian();

        int frameSize = channels * ((bits + 7) / 8);
        AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                rate, bits, channels, frameSize, rate, bigEndian);
        return AudioSystem.getAudioInputStream(target, stream);
    }

    /**
     * Converts interleaved signed PCM bytes to a mono float32 array in the range
     * [-1.0, 1.0], mixing multiple channels by averaging.
     *
     * <p>Supported bit depths: 8, 16, 24, 32.
     *
     * @param bytes        raw PCM bytes (signed, interleaved channels)
     * @param bitsPerSample bit depth of each sample
     * @param bigEndian    {@code true} if the bytes are in big-endian order
     * @param channels     number of interleaved channels
     * @return mono float32 samples
     */
    static float[] rawPcmToMono(byte[] bytes, int bitsPerSample, boolean bigEndian, int channels) {
        int bytesPerSample = (bitsPerSample + 7) / 8;
        int totalSamples = bytes.length / bytesPerSample;
        int frames = totalSamples / channels;

        ByteBuffer buf = ByteBuffer.wrap(bytes)
                .order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        float[] mono = new float[frames];
        for (int i = 0; i < frames; i++) {
            float sum = 0f;
            for (int c = 0; c < channels; c++) {
                sum += readSample(buf, bitsPerSample, bigEndian);
            }
            mono[i] = channels == 1 ? sum : sum / channels;
        }
        return mono;
    }

    /**
     * Reads and normalises one signed PCM sample from {@code buf}.
     *
     * <p>For 24-bit samples the three bytes are assembled manually because
     * {@link ByteBuffer} has no {@code getInt24()} method; byte order is
     * respected via the {@code bigEndian} flag.
     */
    private static float readSample(ByteBuffer buf, int bits, boolean bigEndian) {
        switch (bits) {
            case 8:
                return buf.get() / 128.0f;
            case 16:
                return buf.getShort() / 32768.0f;
            case 24: {
                int b0 = buf.get() & 0xFF, b1 = buf.get() & 0xFF, b2 = buf.get() & 0xFF;
                int val = bigEndian
                        ? (b0 << 16) | (b1 << 8) | b2
                        : b0 | (b1 << 8) | (b2 << 16);
                if ((val & 0x800000) != 0) val |= 0xFF000000; // sign-extend to 32 bits
                return val / 8388608.0f;
            }
            case 32:
                return buf.getInt() / 2147483648.0f;
            default:
                throw new UnsupportedOperationException("Unsupported bit depth: " + bits);
        }
    }

    /**
     * Resamples mono float32 audio from {@code srcRate} to {@code dstRate} using
     * linear interpolation.
     *
     * @param input   input samples
     * @param srcRate source sample rate (Hz)
     * @param dstRate target sample rate (Hz)
     * @return resampled output samples
     */
    static float[] resample(float[] input, float srcRate, int dstRate) {
        if (input.length == 0) {
            return new float[0];
        }
        double ratio = srcRate / dstRate;
        int outLen = (int) Math.round(input.length / ratio);
        float[] output = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            double srcIdx = i * ratio;
            int idx0 = (int) srcIdx;
            int idx1 = Math.min(idx0 + 1, input.length - 1);
            float frac = (float) (srcIdx - idx0);
            output[i] = input[idx0] * (1f - frac) + input[idx1] * frac;
        }
        return output;
    }

    /**
     * Converts 16-bit signed little-endian PCM bytes to float32 samples
     * normalised to the range [-1.0, 1.0].
     */
    public static float[] pcm16ToFloat(byte[] pcm) {
        int samples = pcm.length / 2;
        float[] result = new float[samples];
        ByteBuffer bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < samples; i++) {
            short s = bb.getShort();
            result[i] = s / 32768.0f; // normalize to [-1, 1]
        }

        return result;
    }
}
