package io.jenkins.plugins.checks.api;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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

    private TruncatedString.Builder builder = makeBuilder();

    private TruncatedString.Builder makeBuilder() {
        this.builder = new TruncatedString.Builder().withTruncationText(MESSAGE);

        return builder;
    }

    private String build(final boolean chunkOnChars, final int maxSize) {
        return chunkOnChars ? builder.build().buildByChars(maxSize) : builder.build().buildByBytes(maxSize);
    }

    private String buildRawString() {
        return builder.build().toString();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldBuildStrings(final boolean chunkOnNewlines, final boolean chunkOnChars) {
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
    public void shouldTruncateStrings(final boolean chunkOnNewlines, final boolean chunkOnChars) {
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
    public void shouldHandleEdgeCases(final boolean chunkOnNewlines, final boolean chunkOnChars) {
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
    public void shouldHandleReversedChunking(final boolean chunkOnNewlines, final boolean chunkOnChars) {
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
        assertThat(build(chunkOnChars, 20)).isEqualTo("Truncatedaaaaaa\n");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldHandleEdgeCasesReversed(final boolean chunkOnNewlines, final boolean chunkOnChars) {
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
    public void shouldChunkNewlinesDifferently(final boolean chunkOnNewlines, final boolean chunkOnChars) {
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
    public void shouldTruncateByBytesOrChars(final boolean chunkOnNewlines, final boolean chunkOnChars) {
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
    public void shouldHandleLongCharsInTruncationText(final boolean chunkOnNewlines, final boolean chunkOnChars) {
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

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldForceTruncationText(final boolean chunkOnNewlines, final boolean chunkOnChars) {
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        builder.setForceTruncationText();
        builder.addText("Hello\n");
        assertThat(build(chunkOnChars, 20)).isEqualTo("Hello\nTruncated");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldRemoveEnoughChunksForTheTruncationText(final boolean chunkOnNewlines, final boolean chunkOnChars) {
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        for (int i = 0; i < 15; i++) {
            builder.addText("x\n"); // 2, shorter than the truncation text
        }
        assertThat(build(chunkOnChars, 20)).isEqualTo("x\nx\nx\nx\nx\nTruncated");
        builder.setTruncateStart();
        assertThat(build(chunkOnChars, 20)).isEqualTo("Truncatedx\nx\nx\nx\nx\n");
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void shouldRemoveEnoughChunksForTheForcedTruncationText(final boolean chunkOnNewlines,
            final boolean chunkOnChars) {
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
        builder.setForceTruncationText();
        for (int i = 0; i < 10; i++) {
            builder.addText("x\n"); // 20 in total: fits exactly, but not with the truncation text
        }
        assertThat(build(chunkOnChars, 20)).isEqualTo("x\nx\nx\nx\nx\nTruncated");
        builder.setTruncateStart();
        assertThat(build(chunkOnChars, 20)).isEqualTo("Truncatedx\nx\nx\nx\nx\n");
    }
}
