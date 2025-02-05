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

        ChecksOutputAssert.assertThat(checksOutput)
                .hasTitle(Optional.of(TITLE))
                .hasSummary(Optional.of(SUMMARY))
                .hasText(Optional.of(TEXT));
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
        ChecksOutputAssert.assertThat(copied)
                .hasTitle(Optional.of(TITLE))
                .hasSummary(Optional.of(SUMMARY))
                .hasText(Optional.of(TEXT));
        assertThat(copied.getChecksAnnotations())
                .usingFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(annotations);
        assertThat(copied.getChecksImages())
                .usingFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(images);
    }

    @Test
    void shouldTruncateTextFromStart() {
        String longText = "This is the beginning.\n" + "Middle part.\n".repeat(10) + "This is the end.\n";
        ChecksOutput checksOutput = new ChecksOutputBuilder()
                .withText(longText)
                .build();

        String truncated = checksOutput.getText(75).orElse("");
        
        assertThat(truncated)
                .startsWith("Output truncated.")
                .endsWith("This is the end.\n");
        assertThat(truncated.length()).isLessThanOrEqualTo(75);
    }

    @Test
    void shouldNotTruncateShortText() {
        String shortText = "This is a short text that should not be truncated.";
        ChecksOutput checksOutput = new ChecksOutputBuilder()
                .withText(shortText)
                .build();

        String result = checksOutput.getText(100).orElse("");
        
        assertThat(result)
                .isEqualTo(shortText)
                .doesNotContain("Output truncated.");
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
