package io.jenkins.plugins.checks.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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

    @Parameterized.Parameters(name = "{0}")
    public static Object[][] parameters() {
        return new Object[][]{
                {"Chunks", false},
                {"Newlines", true}
        };
    }

    private TruncatedString.Builder getBuilder() {
        TruncatedString.Builder builder = new TruncatedString.Builder()
                .withTruncationText(MESSAGE);
        if (chunkOnNewlines) {
            return builder.setChunkOnNewlines();
        }
        return builder;
    }

    @Test
    public void shouldBuildStrings() {
        TruncatedString.Builder builder = getBuilder();
        builder.addText("Hello\n");
        assertThat(builder.build()).asString().isEqualTo("Hello\n");
        assertThat(builder.build().build(1000)).isEqualTo("Hello\n");
        builder.addText(", world!");
        assertThat(builder.build()).asString().isEqualTo("Hello\n, world!");
        assertThat(builder.build().build(1000)).isEqualTo("Hello\n, world!");
    }

    @Test
    public void shouldTruncateStrings() {
        TruncatedString.Builder builder = getBuilder();
        builder.addText("xxxxxxxxx\n"); // 10
        assertThat(builder.build().build(20)).isEqualTo("xxxxxxxxx\n");
        builder.addText("yyyy\n"); // 5, doesn't cause overflow
        assertThat(builder.build().build(20)).isEqualTo("xxxxxxxxx\nyyyy\n");
        builder.addText("zzzzzz\n"); // 7, does cause overflow
        assertThat(builder.build().build(20)).isEqualTo("xxxxxxxxx\nTruncated");
    }

    @Test
    public void shouldHandleEdgeCases() {
        TruncatedString.Builder builder = getBuilder();
        assertThat(builder.build().build(10)).isEqualTo("");
        assertThat(builder.build()).asString().isEqualTo("");
        builder.addText("xxxxxxxxxxxxxx\n"); // 15
        assertThat(builder.build().build(10)).isEqualTo("Truncated");
        assertThatThrownBy(() -> {
            builder.build().build(5);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum length is less than truncation text.");
    }

    @Test
    public void shouldHandleReversedChunking() {
        TruncatedString.Builder builder = getBuilder()
                .setTruncateStart();
        builder.addText("zzzz\n"); // 5
        assertThat(builder.build().build(20)).isEqualTo("zzzz\n");
        builder.addText("xxxx\n"); // 5, doesn't cause overflow
        assertThat(builder.build().build(20)).isEqualTo("zzzz\nxxxx\n");
        builder.addText("cccc\n"); // 5, doesn't cause overflow
        assertThat(builder.build().build(20)).isEqualTo("zzzz\nxxxx\ncccc\n");
        builder.addText("aaaaaa\n"); // 7, does cause overflow
        assertThat(builder.build().build(20)).isEqualTo("Truncatedcccc\naaaaaa\n");
    }

    @Test
    public void shouldHandleEdgeCasesReversed() {
        TruncatedString.Builder builder = getBuilder()
                .setTruncateStart();
        assertThat(builder.build().build(10)).isEqualTo("");
        assertThat(builder.build()).asString().isEqualTo("");
        builder.addText("xxxxxxxxxxxxxx\n"); // 15
        assertThat(builder.build().build(10)).isEqualTo("Truncated");
        assertThatThrownBy(() -> {
            builder.build().build(5);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum length is less than truncation text.");
    }

    @Test
    public void shouldChunkNewlinesDifferently() {
        TruncatedString.Builder builder = getBuilder();
        builder.addText("xxxxxxxxxx"); // 10
        builder.addText("yyyyyyyyyyy"); // 11
        assertThat(builder.build().build(20)).isEqualTo(chunkOnNewlines ? "Truncated" : "xxxxxxxxxxTruncated");

        builder = getBuilder();
        builder.addText("wwww\n"); // 5
        builder.addText("xxxx\nyyyy\nzzzzz\n"); // 16
        assertThat(builder.build().build(20)).isEqualTo(chunkOnNewlines ? "wwww\nxxxx\nTruncated" : "wwww\nTruncated");
    }
}
