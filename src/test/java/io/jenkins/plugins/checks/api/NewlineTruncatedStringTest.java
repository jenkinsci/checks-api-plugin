package io.jenkins.plugins.checks.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewlineTruncatedStringTest {
    private static final String MESSAGE = "Truncated";  // length 9

    @Test
    public void shouldBuildStrings() {
        NewlineTruncatedString.Builder builder = new NewlineTruncatedString.Builder()
                .withTruncationText(MESSAGE);
        builder.withString("Hello");
        assertThat(builder.build()).asString().isEqualTo("Hello");
        assertThat(builder.build().build(1000)).isEqualTo("Hello");
        builder.withString("Hello,\n world!");
        assertThat(builder.build()).asString().isEqualTo("Hello,\n world!");
        assertThat(builder.build().build(1000)).isEqualTo("Hello,\n world!");
    }

    @Test
    public void shouldTruncateStrings() {
        NewlineTruncatedString.Builder builder = new NewlineTruncatedString.Builder()
                .withTruncationText(MESSAGE);
        builder.withString("xxxxxxxxx\n"); // 10
        assertThat(builder.build().build(20)).isEqualTo("xxxxxxxxx\n");
        builder.withString("xxxxxxxxx\nyyyy\n"); // 15, doesn't cause overflow
        assertThat(builder.build().build(20)).isEqualTo("xxxxxxxxx\nyyyy\n");
        builder.withString("xxxxxxxxx\nyyyy\nzzzzzz\n"); // 22, does cause overflow
        assertThat(builder.build().build(20)).isEqualTo("xxxxxxxxx\nTruncated");
    }

    @Test
    public void shouldHandleEdgeCases() {
        NewlineTruncatedString.Builder builder = new NewlineTruncatedString.Builder()
                .withTruncationText(MESSAGE);
        assertThat(builder.build().build(10)).isEqualTo("");
        assertThat(builder.build()).asString().isEqualTo("");
        builder.withString("xxxxxxxxxxxxxx\n"); // 15
        assertThat(builder.build().build(10)).isEqualTo("Truncated");
        assertThatThrownBy(() -> {
            builder.build().build(5);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum length is less than truncation text.");
    }

    @Test
    public void shouldHandleReversedChunking() {
        NewlineTruncatedString.Builder builder = new NewlineTruncatedString.Builder()
                .setTruncateStart()
                .withTruncationText(MESSAGE);
        builder.withString("wwww\n"); // 5
        assertThat(builder.build().build(20)).isEqualTo("wwww\n");
        builder.withString("wwww\nxxxx\n"); // 10, doesn't cause overflow
        assertThat(builder.build().build(20)).isEqualTo("wwww\nxxxx\n");
        builder.withString("wwww\nxxxx\nyyyy\n"); // 15, doesn't cause overflow
        assertThat(builder.build().build(20)).isEqualTo("wwww\nxxxx\nyyyy\n");
        builder.withString("wwww\nxxxx\nyyyy\nzzzzzz\n"); // 22, doesn't cause overflow
        assertThat(builder.build().build(20)).isEqualTo("yyyy\nzzzzzz\nTruncated");
    }

    @Test
    public void shouldHandleEdgeCasesReversed() {
        NewlineTruncatedString.Builder builder = new NewlineTruncatedString.Builder()
                .setTruncateStart()
                .withTruncationText(MESSAGE);
        assertThat(builder.build().build(10)).isEqualTo("");
        assertThat(builder.build()).asString().isEqualTo("");
        builder.withString("xxxxxxxxxxxxxx\n"); // 15
        assertThat(builder.build().build(10)).isEqualTo("Truncated");
        assertThatThrownBy(() -> {
            builder.build().build(5);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum length is less than truncation text.");
    }
}