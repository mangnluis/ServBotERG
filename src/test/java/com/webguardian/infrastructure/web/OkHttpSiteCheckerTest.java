package com.webguardian.infrastructure.web;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Protocol;
import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OkHttpSiteCheckerTest {

    @Mock
    private OkHttpClient httpClient;

    @Mock
    private Call call;

    @Mock
    private Response response;

    @Mock
    private ResponseBody responseBody;

    private OkHttpSiteChecker siteChecker;

    @BeforeEach
    public void setup() {
        siteChecker = new OkHttpSiteChecker(httpClient);
    }

    @Test
    public void testSuccessfulCheck() throws IOException {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .checkContent(true)
                .contentCheckString("Welcome")
                .responseTimeThreshold(Duration.ofSeconds(1))
                .build();

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(response.protocol()).thenReturn(Protocol.HTTP_2);
        when(responseBody.string()).thenReturn("Welcome to Example Site");
        when(responseBody.contentLength()).thenReturn(1024L);

        // Act
        CheckResult result = siteChecker.check(site);

        // Assert
        assertNotNull(result);
        assertEquals(CheckResult.CheckStatus.SUCCESS, result.getStatus());
        assertEquals(200, result.getStatusCode());
        assertTrue(result.isContentCheckPassed());
        assertEquals(CheckResult.AlertSeverity.NONE, result.getSeverity());
        assertNotNull(result.getResponseTime());
        assertEquals(1024L, result.getContentSize());

        // Verify
        verify(httpClient).newCall(any(Request.class));
        verify(call).execute();
        verify(response).body();
        verify(responseBody).string();
    }

    @Test
    public void testFailureWithErrorStatus() throws IOException {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .build();

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(false);
        when(response.code()).thenReturn(500);
        when(response.body()).thenReturn(responseBody);
        when(response.message()).thenReturn("Internal Server Error");
        when(responseBody.contentLength()).thenReturn(0L);

        // Act
        CheckResult result = siteChecker.check(site);

        // Assert
        assertNotNull(result);
        assertEquals(CheckResult.CheckStatus.FAILURE, result.getStatus());
        assertEquals(500, result.getStatusCode());
        assertEquals(CheckResult.AlertSeverity.HIGH, result.getSeverity());
        assertNotNull(result.getErrorMessage());
        assertEquals(0L, result.getContentSize());

        // Verify
        verify(httpClient).newCall(any(Request.class));
        verify(call).execute();
    }

    @Test
    public void testContentCheckFailure() throws IOException {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .checkContent(true)
                .contentCheckString("ExpectedContent")
                .build();

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Different content without expected string");
        when(responseBody.contentLength()).thenReturn(512L);

        // Act
        CheckResult result = siteChecker.check(site);

        // Assert
        assertNotNull(result);
        assertEquals(CheckResult.CheckStatus.FAILURE, result.getStatus());
        assertEquals(200, result.getStatusCode());
        assertFalse(result.isContentCheckPassed());
        assertEquals(CheckResult.AlertSeverity.MEDIUM, result.getSeverity());
        assertNotNull(result.getErrorMessage());

        // Verify
        verify(httpClient).newCall(any(Request.class));
        verify(call).execute();
        verify(responseBody).string();
    }

    @Test
    public void testConnectionTimeout() throws IOException {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .build();

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("Connection timed out"));

        // Act
        CheckResult result = siteChecker.check(site);

        // Assert
        assertNotNull(result);
        assertEquals(CheckResult.CheckStatus.TIMEOUT, result.getStatus());
        assertNull(result.getStatusCode());
        assertEquals(CheckResult.AlertSeverity.HIGH, result.getSeverity());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("timed out"));

        // Verify
        verify(httpClient).newCall(any(Request.class));
        verify(call).execute();
    }

    @Test
    public void testSlowResponseWarning() throws IOException {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .responseTimeThreshold(Duration.ofMillis(50))
                .build();

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenAnswer(invocation -> {
            // Simulate a delay that exceeds the threshold
            Thread.sleep(100);
            return response;
        });
        when(response.isSuccessful()).thenReturn(true);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);

        // Act
        CheckResult result = siteChecker.check(site);

        // Assert
        assertNotNull(result);
        assertEquals(CheckResult.CheckStatus.SUCCESS, result.getStatus());
        assertEquals(200, result.getStatusCode());
        assertTrue(result.getResponseTime().toMillis() > 50);
        assertEquals(CheckResult.AlertSeverity.LOW, result.getSeverity());
        
        // Verify
        verify(httpClient).newCall(any(Request.class));
        verify(call).execute();
    }
}