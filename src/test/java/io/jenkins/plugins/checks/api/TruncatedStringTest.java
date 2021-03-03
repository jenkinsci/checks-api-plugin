package io.jenkins.plugins.checks.api;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test behavior of the {@link TruncatedString}.
 */
@SuppressWarnings({"VisibilityModifier", "MissingJavadocMethod"})
@RunWith(Parameterized.class)
public class TruncatedStringTest {
    private static final String MESSAGE = "Truncated";  // length 9

    /**
     * Human readable test name.
     */
    @Parameterized.Parameter
    public String testName;

    /**
     * Parameter for chunking on new lines (or not!).
     */
    @Parameterized.Parameter(1)
    public boolean chunkOnNewlines;

    /**
     * Parameter for chunking on chars (or not!).
     */
    @Parameterized.Parameter(2)
    public boolean chunkOnChars;

    @Parameterized.Parameters(name = "{0}")
    public static Object[][] parameters() {
        return new Object[][]{
                {"Chunks+Bytes", false, false},
                {"Newlines+Bytes", true, false},
                {"Chunks+Chars", false, true},
                {"Newlines+Chars", true, true}
        };
    }

    private TruncatedString.Builder builder;

    @Before
    public void makeBuilder() {
        this.builder = new TruncatedString.Builder()
                .withTruncationText(MESSAGE);
        if (chunkOnNewlines) {
            builder.setChunkOnNewlines();
        }
    }

    private String build(final int maxSize) {
        return chunkOnChars ? builder.build().buildByChars(maxSize) : builder.build().buildByBytes(maxSize);
    }

    private String buildRawString() {
        return builder.build().toString();
    }

    @Test
    public void shouldBuildStrings() {
        builder.addText("Hello\n");
        assertThat(buildRawString()).isEqualTo("Hello\n");
        assertThat(build(1000)).isEqualTo("Hello\n");
        builder.addText(", world!");
        assertThat(buildRawString()).isEqualTo("Hello\n, world!");
        assertThat(build(1000)).isEqualTo("Hello\n, world!");
    }

    @Test
    public void shouldTruncateStrings() {
        builder.addText("xxxxxxxxx\n"); // 10
        assertThat(build(20)).isEqualTo("xxxxxxxxx\n");
        builder.addText("yyyy\n"); // 5, doesn't cause overflow
        assertThat(build(20)).isEqualTo("xxxxxxxxx\nyyyy\n");
        builder.addText("zzzzzz\n"); // 7, does cause overflow
        assertThat(build(20)).isEqualTo("xxxxxxxxx\nTruncated");
    }

    @Test
    public void shouldHandleEdgeCases() {
        assertThat(build(10)).isEqualTo("");
        assertThat(buildRawString()).isEqualTo("");
        builder.addText("xxxxxxxxxxxxxx\n"); // 15
        assertThat(build(10)).isEqualTo("Truncated");
        assertThatThrownBy(() -> {
            build(5);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum length is less than truncation text.");
    }

    @Test
    public void shouldHandleReversedChunking() {
        builder.setTruncateStart();
        builder.addText("zzzz\n"); // 5
        assertThat(build(20)).isEqualTo("zzzz\n");
        builder.addText("xxxx\n"); // 5, doesn't cause overflow
        assertThat(build(20)).isEqualTo("zzzz\nxxxx\n");
        builder.addText("cccc\n"); // 5, doesn't cause overflow
        assertThat(build(20)).isEqualTo("zzzz\nxxxx\ncccc\n");
        builder.addText("aaaaaa\n"); // 7, does cause overflow
        assertThat(build(20)).isEqualTo("Truncatedcccc\naaaaaa\n");
    }

    @Test
    public void shouldHandleEdgeCasesReversed() {
        builder.setTruncateStart();
        assertThat(build(10)).isEqualTo("");
        assertThat(buildRawString()).isEqualTo("");
        builder.addText("xxxxxxxxxxxxxx\n"); // 15
        assertThat(build(10)).isEqualTo("Truncated");
        assertThatThrownBy(() -> {
            build(5);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum length is less than truncation text.");
    }

    @Test
    public void shouldChunkNewlinesDifferently() {
        builder.addText("xxxxxxxxxx"); // 10
        builder.addText("yyyyyyyyyyy"); // 11
        assertThat(build(20)).isEqualTo(chunkOnNewlines ? "Truncated" : "xxxxxxxxxxTruncated");

        makeBuilder();
        builder.addText("wwww\n"); // 5
        builder.addText("xxxx\nyyyy\nzzzzz\n"); // 16
        assertThat(build(20)).isEqualTo(chunkOnNewlines ? "wwww\nxxxx\nTruncated" : "wwww\nTruncated");
    }

    @Test
    public void shouldTruncateByBytesOrChars() {
        builder.addText("☃☃☃\n"); // 3 + 1
        assertThat(buildRawString().length()).isEqualTo(4);
        assertThat(buildRawString().getBytes(StandardCharsets.UTF_8).length).isEqualTo(10);
        assertThat(build(20)).isEqualTo("☃☃☃\n");

        builder.addText("🕴️🕴️\n"); // 2 + 1
        assertThat(buildRawString().length()).isEqualTo(11);
        assertThat(buildRawString().getBytes(StandardCharsets.UTF_8).length).isEqualTo(25);
        assertThat(build(20)).isEqualTo(chunkOnChars ? "☃☃☃\n🕴️🕴️\n" : "☃☃☃\nTruncated");
    }

    @Test
    public void shouldHandleLongCharsInTruncationText() {
        String truncationText = "E_TOO_MUCH_☃";
        assertThat(truncationText.length()).isEqualTo(12);
        assertThat(truncationText.getBytes(StandardCharsets.UTF_8).length).isEqualTo(14);

        builder.withTruncationText(truncationText);
        builder.addText("xxxx\n"); // 5
        builder.addText("x\n"); // 2
        assertThat(build(20)).isEqualTo("xxxx\nx\n");
        builder.addText("xxxxxxxxxxxxxxx"); // 15
        assertThat(build(20)).isEqualTo(chunkOnChars ? "xxxx\nx\nE_TOO_MUCH_☃" : "xxxx\nE_TOO_MUCH_☃");
    }
}
