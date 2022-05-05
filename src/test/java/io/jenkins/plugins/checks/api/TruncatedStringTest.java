package io.jenkins.plugins.checks.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test behavior of the {@link TruncatedString}.
 */
@SuppressWarnings({"VisibilityModifier", "MissingJavadocMethod"})
class TruncatedStringTest {
    private static final String MESSAGE = "Truncated";  // length 9

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "Called by JUnit")
    private static Stream<Arguments> parameters() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(true, true));
    }

    private TruncatedString.Builder builder;

    @BeforeEach
    public void makeBuilder() {
        this.builder = new TruncatedString.Builder().withTruncationText(MESSAGE);
    }

    private String build(final boolean chunkOnChars, final int maxSize) {
        return chunkOnChars ? builder.build().buildByChars(maxSize) : builder.build().buildByBytes(maxSize);
    }

    private String buildRawString() {
        return builder.build().toString();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldBuildStrings(boolean chunkOnNewlines, boolean chunkOnChars) {
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        builder.addText("Hello\n");
        assertThat(buildRawString()).isEqualTo("Hello\n");
        assertThat(build(chunkOnChars, 1000)).isEqualTo("Hello\n");
        builder.addText(", world!");
        assertThat(buildRawString()).isEqualTo("Hello\n, world!");
        assertThat(build(chunkOnChars, 1000)).isEqualTo("Hello\n, world!");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldTruncateStrings(boolean chunkOnNewlines, boolean chunkOnChars) {
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        builder.addText("xxxxxxxxx\n"); // 10
        assertThat(build(chunkOnChars, 20)).isEqualTo("xxxxxxxxx\n");
        builder.addText("yyyy\n"); // 5, doesn't cause overflow
        assertThat(build(chunkOnChars, 20)).isEqualTo("xxxxxxxxx\nyyyy\n");
        builder.addText("zzzzzz\n"); // 7, does cause overflow
        assertThat(build(chunkOnChars, 20)).isEqualTo("xxxxxxxxx\nTruncated");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldHandleEdgeCases(boolean chunkOnNewlines, boolean chunkOnChars) {
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        assertThat(build(chunkOnChars, 10)).isEqualTo("");
        assertThat(buildRawString()).isEqualTo("");
        builder.addText("xxxxxxxxxxxxxx\n"); // 15
        assertThat(build(chunkOnChars, 10)).isEqualTo("Truncated");
        assertThatThrownBy(() -> {
            build(chunkOnChars, 5);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum length is less than truncation text.");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldHandleReversedChunking(boolean chunkOnNewlines, boolean chunkOnChars) {
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        builder.setTruncateStart();
        builder.addText("zzzz\n"); // 5
        assertThat(build(chunkOnChars, 20)).isEqualTo("zzzz\n");
        builder.addText("xxxx\n"); // 5, doesn't cause overflow
        assertThat(build(chunkOnChars, 20)).isEqualTo("zzzz\nxxxx\n");
        builder.addText("cccc\n"); // 5, doesn't cause overflow
        assertThat(build(chunkOnChars, 20)).isEqualTo("zzzz\nxxxx\ncccc\n");
        builder.addText("aaaaaa\n"); // 7, does cause overflow
        assertThat(build(chunkOnChars, 20)).isEqualTo("Truncatedcccc\naaaaaa\n");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldHandleEdgeCasesReversed(boolean chunkOnNewlines, boolean chunkOnChars) {
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        builder.setTruncateStart();
        assertThat(build(chunkOnChars, 10)).isEqualTo("");
        assertThat(buildRawString()).isEqualTo("");
        builder.addText("xxxxxxxxxxxxxx\n"); // 15
        assertThat(build(chunkOnChars, 10)).isEqualTo("Truncated");
        assertThatThrownBy(() -> {
            build(chunkOnChars, 5);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum length is less than truncation text.");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldChunkNewlinesDifferently(boolean chunkOnNewlines, boolean chunkOnChars) {
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        builder.addText("xxxxxxxxxx"); // 10
        builder.addText("yyyyyyyyyyy"); // 11
        assertThat(build(chunkOnChars, 20)).isEqualTo(chunkOnNewlines ? "Truncated" : "xxxxxxxxxxTruncated");

        makeBuilder();
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        builder.addText("wwww\n"); // 5
        builder.addText("xxxx\nyyyy\nzzzzz\n"); // 16
        assertThat(build(chunkOnChars, 20)).isEqualTo(chunkOnNewlines ? "wwww\nxxxx\nTruncated" : "wwww\nTruncated");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldTruncateByBytesOrChars(boolean chunkOnNewlines, boolean chunkOnChars) {
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        builder.addText("☃☃☃\n"); // 3 + 1
        assertThat(buildRawString().length()).isEqualTo(4);
        assertThat(buildRawString().getBytes(StandardCharsets.UTF_8).length).isEqualTo(10);
        assertThat(build(chunkOnChars, 20)).isEqualTo("☃☃☃\n");

        builder.addText("🕴️🕴️\n"); // 2 + 1
        assertThat(buildRawString().length()).isEqualTo(11);
        assertThat(buildRawString().getBytes(StandardCharsets.UTF_8).length).isEqualTo(25);
        assertThat(build(chunkOnChars, 20)).isEqualTo(chunkOnChars ? "☃☃☃\n🕴️🕴️\n" : "☃☃☃\nTruncated");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldHandleLongCharsInTruncationText(boolean chunkOnNewlines, boolean chunkOnChars) {
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        String truncationText = "E_TOO_MUCH_☃";
        assertThat(truncationText.length()).isEqualTo(12);
        assertThat(truncationText.getBytes(StandardCharsets.UTF_8).length).isEqualTo(14);

        builder.withTruncationText(truncationText);
        builder.addText("xxxx\n"); // 5
        builder.addText("x\n"); // 2
        assertThat(build(chunkOnChars, 20)).isEqualTo("xxxx\nx\n");
        builder.addText("xxxxxxxxxxxxxxx"); // 15
        assertThat(build(chunkOnChars, 20)).isEqualTo(chunkOnChars ? "xxxx\nx\nE_TOO_MUCH_☃" : "xxxx\nE_TOO_MUCH_☃");
    }
}
