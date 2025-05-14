package com.webguardian.core.entities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.LocalDateTime;

public class CheckResultTest {

    @Test
    public void testCreateCheckResult() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .build();

        LocalDateTime now = LocalDateTime.now();

        // Act
        CheckResult result = new CheckResult();
        result.setId(1L);
        result.setSite(site);
        result.setTimestamp(now);
        result.setStatusCode(200);
        result.setResponseTime(Duration.ofMillis(150));
        result.setContentSize(1024L);
        result.setStatus(CheckResult.CheckStatus.SUCCESS);
        result.setContentCheckPassed(true);
        result.setSslCheckPassed(true);
        result.setErrorMessage(null);
        result.setSeverity(CheckResult.AlertSeverity.NONE);

        // Assert
        assertEquals(1L, result.getId());
        assertEquals(site, result.getSite());
        assertEquals(now, result.getTimestamp());
        assertEquals(200, result.getStatusCode());
        assertEquals(Duration.ofMillis(150), result.getResponseTime());
        assertEquals(1024L, result.getContentSize());
        assertEquals(CheckResult.CheckStatus.SUCCESS, result.getStatus());
        assertTrue(result.isContentCheckPassed());
        assertTrue(result.isSslCheckPassed());
        assertNull(result.getErrorMessage());
        assertEquals(CheckResult.AlertSeverity.NONE, result.getSeverity());
    }

    @Test
    public void testBuilderPattern() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .build();

        LocalDateTime now = LocalDateTime.now();

        // Act
        CheckResult result = CheckResult.builder()
                .id(2L)
                .site(site)
                .timestamp(now)
                .statusCode(500)
                .responseTime(Duration.ofMillis(3000))
                .contentSize(0)
                .status(CheckResult.CheckStatus.FAILURE)
                .contentCheckPassed(false)
                .sslCheckPassed(true)
                .errorMessage("Internal Server Error")
                .severity(CheckResult.AlertSeverity.HIGH)
                .build();

        // Assert
        assertEquals(2L, result.getId());
        assertEquals(site, result.getSite());
        assertEquals(now, result.getTimestamp());
        assertEquals(500, result.getStatusCode());
        assertEquals(Duration.ofMillis(3000), result.getResponseTime());
        assertEquals(0, result.getContentSize());
        assertEquals(CheckResult.CheckStatus.FAILURE, result.getStatus());
        assertFalse(result.isContentCheckPassed());
        assertTrue(result.isSslCheckPassed());
        assertEquals("Internal Server Error", result.getErrorMessage());
        assertEquals(CheckResult.AlertSeverity.HIGH, result.getSeverity());
    }

    @Test
    public void testEqualsAndHashCode() {
        // Arrange
        CheckResult result1 = CheckResult.builder()
                .id(1L)
                .statusCode(200)
                .build();

        CheckResult result2 = CheckResult.builder()
                .id(1L)
                .statusCode(200)
                .build();

        CheckResult result3 = CheckResult.builder()
                .id(2L)
                .statusCode(404)
                .build();

        // Assert
        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1, result3);
        assertNotEquals(result1.hashCode(), result3.hashCode());
    }

    @Test
    public void testFailureScenario() {
        // Act
        CheckResult result = CheckResult.builder()
                .statusCode(404)
                .responseTime(Duration.ofMillis(200))
                .status(CheckResult.CheckStatus.FAILURE)
                .contentCheckPassed(false)
                .severity(CheckResult.AlertSeverity.MEDIUM)
                .errorMessage("Page Not Found")
                .build();

        // Assert
        assertEquals(404, result.getStatusCode());
        assertEquals(CheckResult.CheckStatus.FAILURE, result.getStatus());
        assertFalse(result.isContentCheckPassed());
        assertEquals(CheckResult.AlertSeverity.MEDIUM, result.getSeverity());
        assertEquals("Page Not Found", result.getErrorMessage());
    }

    @Test
    public void testTimeoutScenario() {
        // Act
        CheckResult result = CheckResult.builder()
                .status(CheckResult.CheckStatus.TIMEOUT)
                .responseTime(null)
                .severity(CheckResult.AlertSeverity.HIGH)
                .errorMessage("Connection timed out")
                .build();

        // Assert
        assertNull(result.getStatusCode());
        assertEquals(CheckResult.CheckStatus.TIMEOUT, result.getStatus());
        assertNull(result.getResponseTime());
        assertEquals(CheckResult.AlertSeverity.HIGH, result.getSeverity());
        assertEquals("Connection timed out", result.getErrorMessage());
    }
}
