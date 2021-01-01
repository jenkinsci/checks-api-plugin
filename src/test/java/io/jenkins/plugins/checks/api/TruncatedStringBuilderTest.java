package io.jenkins.plugins.checks.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TruncatedStringBuilderTest {

    private static final String MESSAGE = "Truncated";  // length 9

    @Test
    public void shouldBuildStrings() {
        TruncatedStringBuilder builder = new TruncatedStringBuilder(1000, MESSAGE);
        builder.append("Hello");
        assertThat(builder).asString().isEqualTo("Hello");
        assertThat(builder.isFull()).isFalse();
        builder.append(", world!");
        assertThat(builder).asString().isEqualTo("Hello, world!");
        assertThat(builder.isFull()).isFalse();
    }

    @Test
    public void shouldTruncateStrings() {
        TruncatedStringBuilder builder = new TruncatedStringBuilder(20, MESSAGE);
        builder.append("xxxxxxxxxx"); // 10
        assertThat(builder).asString().isEqualTo("xxxxxxxxxx");
        assertThat(builder.isFull()).isFalse();
        builder.append("yyyyy"); // 5, causes overflow
        assertThat(builder).asString().isEqualTo("xxxxxxxxxxTruncated");
        assertThat(builder.isFull()).isTrue();
    }

    @Test
    public void shouldStopAddingOnceTruncated() {
        TruncatedStringBuilder builder = new TruncatedStringBuilder(20, MESSAGE);
        builder.append("xxxxx"); // 5
        assertThat(builder).asString().isEqualTo("xxxxx");
        assertThat(builder.isFull()).isFalse();
        builder.append("yyyyyyyyyy"); // 10, causes overflow
        assertThat(builder).asString().isEqualTo("xxxxxTruncated");
        assertThat(builder.isFull()).isTrue();
        builder.append("zzzzz"); // 5, does not cause overflow but builder is now marked as being full!
        assertThat(builder).asString().isEqualTo("xxxxxTruncated");
        assertThat(builder.isFull()).isTrue();
    }

}
