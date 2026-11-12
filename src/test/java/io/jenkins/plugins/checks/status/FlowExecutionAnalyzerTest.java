package io.jenkins.plugins.checks.status;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kohsuke.stapler.framework.io.ByteBuffer;
import org.kohsuke.stapler.framework.io.LargeText;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleNote;
import io.jenkins.plugins.checks.api.TruncatedString;

import static io.jenkins.plugins.checks.status.FlowExecutionAnalyzer.MAX_LOG_SIZE_TO_READ;
import static io.jenkins.plugins.checks.status.FlowExecutionAnalyzer.MIN_LOG_SIZE_TO_READ;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests how {@link FlowExecutionAnalyzer} reads the end of a step log.
 */
@SuppressWarnings({"VisibilityModifier", "MissingJavadocMethod"})
class FlowExecutionAnalyzerTest {
    private static final int MAX_SIZE = 65_000;
    private static final String TRUNCATED = AbstractRunAnalyzer.TRUNCATED_MESSAGE_BUILD_LOG;
    /** With lines of 64 bytes, the first read (a power of 2 from the end) starts at a line start. */
    private static final int LINES_OF_64_BYTES = MIN_LOG_SIZE_TO_READ / 64 + 1_000;

    @Test
    void shouldReturnNullForEmptyLog() throws IOException {
        assertThat(FlowExecutionAnalyzer.getLogTail(log(new byte[0]), MAX_SIZE)).isNull();
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "Called by JUnit")
    private static Stream<Arguments> logs() {
        Stream<Arguments> fixed = Stream.of(
                Arguments.of("shorter than the max size", lines("short line\n", 100, "")),
                Arguments.of("longer than the max size, shorter than the first read",
                        lines("x".repeat(99) + "\n", 2_000, "")),
                Arguments.of("cut at a line start", lines("x".repeat(63) + "\n", LINES_OF_64_BYTES, "")),
                Arguments.of("cut at a newline", lines("x".repeat(63) + "\n", LINES_OF_64_BYTES, "x".repeat(63))),
                Arguments.of("last line longer than the max size", lines("line\n", 300_000, "y".repeat(MAX_SIZE + 1))),
                Arguments.of("last line longer than the first read",
                        lines("line\n", 300_000, "y".repeat(MIN_LOG_SIZE_TO_READ + 10) + "\n")),
                Arguments.of("last line without newline longer than the first read",
                        lines("line\n", 100, "y".repeat(MIN_LOG_SIZE_TO_READ + 10))),
                Arguments.of("no newline", lines("", 0, "y".repeat(MIN_LOG_SIZE_TO_READ + 10))),
                Arguments.of("lines shorter than the truncation text", lines("x\n", 40_000, "")),
                Arguments.of("empty lines", lines("\n", 80_000, "")));
        // shift the cut through every byte of a 4-byte character and through a console note
        Stream<Arguments> shifted = IntStream.range(0, 4).boxed().flatMap(shift -> Stream.of(
                Arguments.of("cut in a 4-byte character, shift " + shift,
                        lines("\uD83D\uDE00".repeat(1_000) + "\n", 300, "a".repeat(shift))),
                Arguments.of("cut in a console note, shift " + shift,
                        lines("a".repeat(300) + consoleNote(1_000) + "b\n", 1_000, "a".repeat(shift)))));
        Stream<Arguments> random = IntStream.rangeClosed(1, 8)
                .mapToObj(seed -> Arguments.of("mixed content, seed " + seed, mixed(seed)));
        return Stream.of(fixed, shifted, random).flatMap(s -> s);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("logs")
    void shouldReturnTheSameTextAsReadingTheWholeLog(final String name, final byte[] content) throws IOException {
        String tail = FlowExecutionAnalyzer.getLogTail(log(content), MAX_SIZE);

        assertThat(tail).isEqualTo(readWholeLog(log(content), MAX_SIZE));
        assertThat(tail).doesNotContain("\uFFFD", ConsoleNote.PREAMBLE_STR);
        assertThat(tail.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(MAX_SIZE);
    }

    @Test
    void shouldReadOnlyTheEndOfAVeryLargeLog() throws IOException {
        String line = "line of a very large log\n";
        GeneratedLog source = new GeneratedLog(line, 8L * 1024 * 1024 * 1024); // 8 GiB, more than a byte[] can hold

        String tail = FlowExecutionAnalyzer.getLogTail(source.toLogText(), MAX_SIZE);

        assertThat(source.bytesRead).isLessThanOrEqualTo(MIN_LOG_SIZE_TO_READ);
        assertThat(tail).startsWith(TRUNCATED);
        assertThat(tail.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(MAX_SIZE);
        String kept = tail.substring(TRUNCATED.length());
        assertThat(kept.length()).isGreaterThan(MAX_SIZE - 2 * line.length());
        assertThat(kept.split("(?<=\n)")).containsOnly(line);
    }

    @Test
    void shouldStopReadingAtTheReadLimit() throws IOException {
        GeneratedLog source = new GeneratedLog("y", 8L * 1024 * 1024 * 1024); // one line of 8 GiB, no newline

        String tail = FlowExecutionAnalyzer.getLogTail(source.toLogText(), MAX_SIZE);

        assertThat(source.bytesRead).isLessThanOrEqualTo(2L * MAX_LOG_SIZE_TO_READ);
        assertThat(tail).isEqualTo(TRUNCATED);
    }

    @Test
    void shouldKeepOnlyWholeLinesWhenTheEndOfTheLogIsMostlyConsoleNotes() throws IOException {
        // 5 bytes of text and a 1.3 KB console note per line: the last 16 MiB hold less text than the max size
        byte[] content = lines("text" + consoleNote(1_000) + "\n", 14_000, "");

        String tail = FlowExecutionAnalyzer.getLogTail(log(content), MAX_SIZE);

        assertThat(tail).startsWith(TRUNCATED);
        String kept = tail.substring(TRUNCATED.length());
        assertThat(kept).isNotEmpty();
        assertThat(kept.split("(?<=\n)")).containsOnly("text\n");
        assertThat(readWholeLog(log(content), MAX_SIZE)).endsWith(kept);
    }

    @Test
    void shouldNotExceedTheMaxSizeWhenTheTruncationTextIsForcedBeforeShortLines() throws IOException {
        // more than the read limit of ANSI codes, then exactly the max size of text in lines of 2 bytes
        byte[] content = lines("", 0, "\u001B[0m".repeat(MAX_LOG_SIZE_TO_READ / 4 + 1024 * 1024)
                + "x\n".repeat(MAX_SIZE / 2));

        String tail = FlowExecutionAnalyzer.getLogTail(log(content), MAX_SIZE);

        assertThat(tail).startsWith(TRUNCATED);
        assertThat(tail.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(MAX_SIZE);
        String kept = tail.substring(TRUNCATED.length());
        assertThat(kept.length()).isGreaterThan(MAX_SIZE - TRUNCATED.length() - 2);
        assertThat(kept.split("(?<=\n)")).containsOnly("x\n");
        assertThat(readWholeLog(log(content), MAX_SIZE)).endsWith(kept);
    }

    /**
     * Reads the whole log, as the analyzer did before it read only the end of the log.
     */
    private static String readWholeLog(final AnnotatedLargeText<?> logText, final int maxSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (logText.writeLogTo(0, out) == 0) {
            return null;
        }
        String log = out.toString(StandardCharsets.UTF_8).replaceAll("\u001B\\[[;\\d]*m", "");
        return new TruncatedString.Builder()
                .setChunkOnNewlines()
                .setTruncateStart()
                .withTruncationText(TRUNCATED)
                .addText(log)
                .build()
                .buildByBytes(maxSize);
    }

    private static AnnotatedLargeText<Void> log(final byte[] content) throws IOException {
        ByteBuffer buffer = new ByteBuffer();
        buffer.write(content, 0, content.length);
        return new AnnotatedLargeText<>(buffer, StandardCharsets.UTF_8, true, null);
    }

    private static byte[] lines(final String line, final int count, final String last) {
        return (line.repeat(count) + last).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] mixed(final long seed) {
        Random random = new Random(seed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (out.size() < MAX_LOG_SIZE_TO_READ + 300_000) {
            String line;
            switch (random.nextInt(6)) {
                case 0:
                    line = "plain " + "x".repeat(random.nextInt(200)) + "\n";
                    break;
                case 1:
                    line = "\u00e4\u20ac\uD83D\uDE00 ".repeat(1 + random.nextInt(50)) + "\n";
                    break;
                case 2:
                    line = "\u001B[31mred\u001B[0m and \u001B[1;32mgreen\u001B[m\n";
                    break;
                case 3:
                    line = "windows line\r\n";
                    break;
                case 4:
                    line = "see " + consoleNote(random.nextInt(600)) + "link\n";
                    break;
                default:
                    line = "\n";
                    break;
            }
            out.writeBytes(line.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    /**
     * Returns a console note in the format that {@link ConsoleNote#skip} reads, without a MAC.
     */
    private static String consoleNote(final int payloadSize) {
        try {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(payload);
            data.writeInt(payloadSize);
            data.write(new byte[payloadSize]);
            data.flush();
            return ConsoleNote.PREAMBLE_STR + Base64.getEncoder().encodeToString(payload.toByteArray())
                    + ConsoleNote.POSTAMBLE_STR;
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A log that repeats one line, generated while it is read. Counts the bytes that are read.
     */
    private static class GeneratedLog implements LargeText.Source {
        private final byte[] line;
        private final long length;
        long bytesRead;

        GeneratedLog(final String line, final long length) {
            this.line = line.getBytes(StandardCharsets.UTF_8);
            this.length = length;
        }

        @Override
        public LargeText.Session open() {
            return new LargeText.Session() {
                private long position;

                @Override
                public long skip(final long n) {
                    long skipped = Math.min(n, length - position);
                    position += skipped;
                    return skipped;
                }

                @Override
                public int read(final byte[] buf) {
                    return read(buf, 0, buf.length);
                }

                @Override
                public int read(final byte[] buf, final int offset, final int count) {
                    if (position >= length) {
                        return -1;
                    }
                    int n = (int) Math.min(count, length - position);
                    for (int i = 0; i < n; i++) {
                        buf[offset + i] = line[(int) ((position + i) % line.length)];
                    }
                    position += n;
                    bytesRead += n;
                    return n;
                }

                @Override
                public void close() {
                    // nothing to close
                }
            };
        }

        AnnotatedLargeText<Void> toLogText() {
            return new AnnotatedLargeText<>(this, StandardCharsets.UTF_8, true, null);
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public boolean exists() {
            return true;
        }
    }
}
