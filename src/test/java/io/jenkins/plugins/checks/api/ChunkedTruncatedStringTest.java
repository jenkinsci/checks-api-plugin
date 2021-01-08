package io.jenkins.plugins.checks.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkedTruncatedStringTest {
    private static final String MESSAGE = "Truncated";  // length 9

    @Test
    public void shouldBuildStrings() {
        ChunkedTruncatedString.Builder builder = new ChunkedTruncatedString.Builder()
                .withTruncationText(MESSAGE);
        builder.addText("Hello");
        assertThat(builder.build()).asString().isEqualTo("Hello");
        assertThat(builder.build().build(1000)).isEqualTo("Hello");
        builder.addText(", world!");
        assertThat(builder.build()).asString().isEqualTo("Hello, world!");
        assertThat(builder.build().build(1000)).isEqualTo("Hello, world!");
    }

    @Test
    public void shouldTruncateStrings() {
        ChunkedTruncatedString.Builder builder = new ChunkedTruncatedString.Builder()
                .withTruncationText(MESSAGE);
        builder.addText("xxxxxxxxxx"); // 10
        assertThat(builder.build().build(20)).isEqualTo("xxxxxxxxxx");
        builder.addText("yyyyy"); // 5, doesn't cause overflow
        assertThat(builder.build().build(20)).isEqualTo("xxxxxxxxxxyyyyy");
        builder.addText("zzzzzzz"); // 7, does cause overflow
        assertThat(builder.build().build(20)).isEqualTo("xxxxxxxxxxTruncated");
    }

    @Test
    public void shouldHandleEdgeCases() {
        ChunkedTruncatedString.Builder builder = new ChunkedTruncatedString.Builder()
                .withTruncationText(MESSAGE);
        assertThat(builder.build().build(10)).isEqualTo("");
        assertThat(builder.build()).asString().isEqualTo("");
        builder.addText("xxxxxxxxxxxxxxx"); // 15
        assertThat(builder.build().build(10)).isEqualTo("Truncated");
        assertThatThrownBy(() -> {
            builder.build().build(5);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum length is less than truncation text.");
    }

    @Test
    public void shouldHandleReversedChunking() {
        ChunkedTruncatedString.Builder builder = new ChunkedTruncatedString.Builder()
                .setTruncateStart()
                .withTruncationText(MESSAGE);
        builder.addText("zzzzz"); // 5
        assertThat(builder.build().build(20)).isEqualTo("zzzzz");
        builder.addText("xxxxx"); // 5, doesn't cause overflow
        assertThat(builder.build().build(20)).isEqualTo("zzzzzxxxxx");
        builder.addText("ccccc"); // 5, doesn't cause overflow
        assertThat(builder.build().build(20)).isEqualTo("zzzzzxxxxxccccc");
        builder.addText("aaaaaaa"); // 7, doesn't cause overflow
        assertThat(builder.build().build(20)).isEqualTo("cccccaaaaaaaTruncated");
    }

    @Test
    public void shouldHandleEdgeCasesReversed() {
        ChunkedTruncatedString.Builder builder = new ChunkedTruncatedString.Builder()
                .setTruncateStart()
                .withTruncationText(MESSAGE);
        assertThat(builder.build().build(10)).isEqualTo("");
        assertThat(builder.build()).asString().isEqualTo("");
        builder.addText("xxxxxxxxxxxxxxx"); // 15
        assertThat(builder.build().build(10)).isEqualTo("Truncated");
        assertThatThrownBy(() -> {
            builder.build().build(5);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum length is less than truncation text.");
    }

}