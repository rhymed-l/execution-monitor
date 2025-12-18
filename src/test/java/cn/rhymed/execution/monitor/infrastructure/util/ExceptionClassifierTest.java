package cn.rhymed.execution.monitor.infrastructure.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExceptionClassifier 单元测试
 * TDD: 测试先行
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
class ExceptionClassifierTest {

    private ExceptionClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ExceptionClassifier(
                Arrays.asList("TimeoutException", "IOException"),
                Arrays.asList("IllegalArgumentException", "ValidationException")
        );
    }

    @Test
    void should_classify_retryable_exception() {
        // Given
        Throwable exception = new SocketTimeoutException("Connection timeout");

        // When
        boolean isRetryable = classifier.isRetryable(exception);

        // Then
        assertTrue(isRetryable);
    }

    @Test
    void should_classify_ignorable_exception() {
        // Given
        Throwable exception = new IllegalArgumentException("Invalid parameter");

        // When
        boolean isIgnorable = classifier.isIgnorable(exception);

        // Then
        assertTrue(isIgnorable);
    }

    @Test
    void should_not_classify_unknown_exception_as_retryable() {
        // Given
        ExceptionClassifier strictClassifier = new ExceptionClassifier(
                Collections.singletonList("TimeoutException"),
                Collections.emptyList()
        );
        Throwable exception = new RuntimeException("Unknown error");

        // When
        boolean isRetryable = strictClassifier.isRetryable(exception);

        // Then
        assertFalse(isRetryable);
    }

    @Test
    void should_match_by_class_name_substring() {
        // Given
        Throwable exception = new IOException("IO error");

        // When
        boolean isRetryable = classifier.isRetryable(exception);

        // Then
        assertTrue(isRetryable);
    }

    @Test
    void should_match_parent_exception_class() {
        // Given - SocketTimeoutException extends IOException
        Throwable exception = new SocketTimeoutException();

        // When
        boolean isRetryable = classifier.isRetryable(exception);

        // Then
        assertTrue(isRetryable); // 应该匹配 IOException
    }

    @Test
    void should_prioritize_ignorable_over_retryable() {
        // Given - 如果异常同时匹配可忽略和可重试,应该优先可忽略
        ExceptionClassifier conflictClassifier = new ExceptionClassifier(
                Collections.singletonList("Exception"),
                Collections.singletonList("IllegalArgumentException")
        );
        Throwable exception = new IllegalArgumentException();

        // When
        boolean isIgnorable = conflictClassifier.isIgnorable(exception);
        boolean isRetryable = conflictClassifier.isRetryable(exception);

        // Then
        assertTrue(isIgnorable);
        assertTrue(isRetryable); // 也匹配retryable,但ignorable优先级更高
    }

    @Test
    void should_handle_null_exception() {
        // When & Then
        assertFalse(classifier.isRetryable(null));
        assertFalse(classifier.isIgnorable(null));
    }

    @Test
    void should_handle_empty_configuration() {
        // Given
        ExceptionClassifier emptyClassifier = new ExceptionClassifier(
                Collections.emptyList(),
                Collections.emptyList()
        );
        Throwable exception = new RuntimeException();

        // When & Then
        assertFalse(emptyClassifier.isRetryable(exception));
        assertFalse(emptyClassifier.isIgnorable(exception));
    }

    @Test
    void should_get_classification_result() {
        // Given
        Throwable retryable = new SocketTimeoutException();
        Throwable ignorable = new IllegalArgumentException();
        Throwable neither = new NullPointerException();

        // When
        ExceptionClassifier.ClassificationResult result1 = classifier.classify(retryable);
        ExceptionClassifier.ClassificationResult result2 = classifier.classify(ignorable);
        ExceptionClassifier.ClassificationResult result3 = classifier.classify(neither);

        // Then
        assertEquals(ExceptionClassifier.ClassificationResult.RETRYABLE, result1);
        assertEquals(ExceptionClassifier.ClassificationResult.IGNORABLE, result2);
        assertEquals(ExceptionClassifier.ClassificationResult.NEITHER, result3);
    }

    @Test
    void should_match_exact_class_name() {
        // Given
        ExceptionClassifier exactClassifier = new ExceptionClassifier(
                Collections.singletonList("java.lang.IllegalStateException"),
                Collections.emptyList()
        );
        Throwable exception = new IllegalStateException();

        // When
        boolean isRetryable = exactClassifier.isRetryable(exception);

        // Then
        assertTrue(isRetryable);
    }

    @Test
    void should_match_cause_exception() {
        // Given
        IOException cause = new IOException("Root cause");
        RuntimeException wrapper = new RuntimeException("Wrapper", cause);

        // When
        boolean isRetryable = classifier.isRetryable(wrapper);

        // Then
        assertTrue(isRetryable); // 应该检查cause
    }
}
