package io.jenkins.plugins.checks.api;

import io.jenkins.plugins.checks.api.ChecksAnnotation.ChecksAnnotationBuilder;
import io.jenkins.plugins.checks.api.ChecksAnnotation.ChecksAnnotationLevel;
import io.jenkins.plugins.checks.api.ChecksOutput.ChecksOutputBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksOutputTest {
    private static final String TITLE = "Coverage Report";
    private static final String SUMMARY = "All code have been covered";
    private static final String TEXT = "# Markdown Supported Text";

    @Test
    void shouldBuildCorrectlyWithAllFields() {
        final List<ChecksAnnotation> annotations = createAnnotations();
        final List<ChecksImage> images = createImages();
        final ChecksOutput checksOutput = new ChecksOutputBuilder()
                .withTitle(TITLE)
                .withSummary(SUMMARY)
                .withText(TEXT)
                .withAnnotations(annotations.subList(0, 1))
                .addAnnotation(annotations.get(1))
                .withImages(images.subList(0, 1))
                .addImage(images.get(1))
                .build();

        assertThat(checksOutput.getTitle()).isEqualTo(Optional.of(TITLE));
        assertThat(checksOutput.getSummary()).isEqualTo(Optional.of(SUMMARY));
        assertThat(checksOutput.getText()).isEqualTo(Optional.of(TEXT));
        assertThat(checksOutput.getChecksAnnotations())
                .usingFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(annotations);
        assertThat(checksOutput.getChecksImages())
                .usingFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(images);

        assertThat(checksOutput).hasToString("ChecksOutput{"
                + "title='Coverage Report', summary='All code have been covered', text='# Markdown Supported Text'"
                + ", annotations=" + annotations
                + ", images=" + images
                + "}");
    }

    @Test
    void shouldBuildCorrectlyWhenAddingAnnotations() {
        final ChecksOutputBuilder builder = new ChecksOutputBuilder();
        final List<ChecksAnnotation> annotations = createAnnotations();
        annotations.forEach(builder::addAnnotation);

        assertThat(builder.build().getChecksAnnotations())
                .usingFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(annotations);
    }

    @Test
    void shouldBuildCorrectlyWhenAddingImages() {
        final ChecksOutputBuilder builder = new ChecksOutputBuilder();
        final List<ChecksImage> images = createImages();
        images.forEach(builder::addImage);

        assertThat(builder.build().getChecksImages())
                .usingFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(images);
    }

    @Test
    void shouldCopyConstructCorrectly() {
        final List<ChecksAnnotation> annotations = createAnnotations();
        final List<ChecksImage> images = createImages();
        final ChecksOutput checksOutput = new ChecksOutputBuilder()
                .withTitle(TITLE)
                .withSummary(SUMMARY)
                .withText(TEXT)
                .withAnnotations(annotations.subList(0, 1))
                .addAnnotation(annotations.get(1))
                .withImages(images.subList(0, 1))
                .addImage(images.get(1))
                .build();

        ChecksOutput copied = new ChecksOutput(checksOutput);
        assertThat(copied.getTitle()).isEqualTo(Optional.of(TITLE));
        assertThat(copied.getSummary()).isEqualTo(Optional.of(SUMMARY));
        assertThat(copied.getText()).isEqualTo(Optional.of(TEXT));
        assertThat(copied.getChecksAnnotations())
                .usingFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(annotations);
        assertThat(copied.getChecksImages())
                .usingFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(images);
    }

    @Test
    void shouldTruncateSummaryLogFromStart() {
        String summary = "### `Fails / Shell Script`\n"
                + "Error in `sh` step.\n"
                + "```\n"
                + "script returned exit code 1\n"
                + "```\n"
                + "<details>\n"
                + "<summary>Build log</summary>\n"
                + "\n"
                + "```\n"
                + "+ echo 'First line of log'\n"
                + "First line of log\n"
                + "+ echo 'Second line of log'\n"
                + "Second line of log\n"
                + "+ echo 'Third line of log'\n"
                + "Third line of log\n"
                + "+ exit 1\n"
                + "```\n"
                + "</details>\n"
                + "\n";
        int maxSize = 200;

        ChecksOutput checksOutput = new ChecksOutputBuilder()
                .withSummary(summary)
                .build();

        String truncated = checksOutput.getSummary(maxSize).orElse("");
        String expected = "### `Fails / Shell Script`\n"
                + "Error in `sh` step.\n"
                + "```\n"
                + "script returned exit code 1\n"
                + "```\n"
                + "<details>\n"
                + "<summary>Build log</summary>\n"
                + "\n"
                + "```\n"
                + "Build log truncated.\n"
                + "Third line of log\n"
                + "+ exit 1\n"
                + "```\n"
                + "</details>\n"
                + "\n";

        assertThat(truncated)
                .isEqualTo(expected);
        assertThat(truncated.length()).isLessThanOrEqualTo(maxSize);
    }

    @Test
    void shouldHandleNullSummary() {
        ChecksOutput checksOutput = new ChecksOutputBuilder().build();
        assertThat(checksOutput.getSummary(100)).isEmpty();
    }

    @Test
    void shouldTruncateSummaryFromEndWithoutBuildLog() {
        String summary = "### Test Results\n"
                + "Found 5 test failures in the build:\n"
                + "- `TestClass1.testMethod1`: Assertion failed, expected true but was false\n"
                + "- `TestClass2.testMethod2`: NullPointerException at line 42\n"
                + "- `TestClass3.testMethod3`: Expected exception was not thrown\n"
                + "- `TestClass4.testMethod4`: Timeout after 5 seconds\n"
                + "- `TestClass5.testMethod5`: Invalid test data\n";

        ChecksOutput checksOutput = new ChecksOutputBuilder()
                .withSummary(summary)
                .build();

        // Test with a size that only fits the header and first failure
        String truncated = checksOutput.getSummary(70).orElse("");
        assertThat(truncated)
                .startsWith("### Test Results\n")
                .contains("Found 5 test failures")
                .doesNotContain("TestClass5.testMethod5")
                .contains("Output truncated.")
                .hasSizeLessThanOrEqualTo(70);

        // Verify that with sufficient size, we get the full content
        assertThat(checksOutput.getSummary(500).orElse(""))
                .isEqualTo(summary);
    }

    @Test
    void shouldHandleVerySmallMaxSize() {
        String summary = "### `Fails / Shell Script`\n"
                + "<details>\n"
                + "<summary>Build log</summary>\n"
                + "```\n"
                + "+ echo 'First line'\n"
                + "First line\n"
                + "```\n"
                + "</details>\n";

        ChecksOutput checksOutput = new ChecksOutputBuilder()
                .withSummary(summary)
                .build();

        // Size so small that only header and truncation message fit
        String truncated = checksOutput.getSummary(50).orElse("");
        assertThat(truncated)
                .startsWith("### `Fails / Shell Script`\n")
                .doesNotContain("<details>")
                .doesNotContain("<summary>Build log</summary>")
                .doesNotContain("Build log truncated.")
                .contains("Output truncated.")
                .hasSizeLessThanOrEqualTo(50);
    }

    @Test
    void shouldPreserveMarkdownStructure() {
        String summary = "### `Test`\n"
                + "<details>\n"
                + "<summary>Build log</summary>\n"
                + "```\n"
                + "Line 1\n"
                + "Line 2\n"
                + "Line 3\n"
                + "```\n"
                + "</details>\n";

        ChecksOutput checksOutput = new ChecksOutputBuilder()
                .withSummary(summary)
                .build();

        String truncated = checksOutput.getSummary(100).orElse("");
        assertThat(truncated)
                .contains("<details>")
                .contains("</details>")
                .contains("```\n")
                .endsWith("```\n</details>\n")
                .hasSizeLessThanOrEqualTo(100);
    }

    private List<ChecksAnnotation> createAnnotations() {
        final ChecksAnnotationBuilder builder = new ChecksAnnotationBuilder()
                .withPath("src/main/java/1.java")
                .withStartLine(0)
                .withEndLine(10)
                .withAnnotationLevel(ChecksAnnotationLevel.WARNING)
                .withMessage("first annotation");

        final List<ChecksAnnotation> annotations = new ArrayList<>();
        annotations.add(builder.withTitle("first").build());
        annotations.add(builder.withTitle("second").build());
        return annotations;
    }

    private List<ChecksImage> createImages() {
        final List<ChecksImage> images = new ArrayList<>();
        images.add(new ChecksImage("image_1", "https://www.image_1.com", null));
        images.add(new ChecksImage("image_2", "https://www.image_2.com", null));
        return images;
    }
}
