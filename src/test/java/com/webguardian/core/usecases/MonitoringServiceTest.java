package com.webguardian.core.usecases;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.ports.NotificationPort;
import com.webguardian.core.ports.SiteCheckerPort;
import com.webguardian.core.ports.SiteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MonitoringServiceTest {

    @Mock
    private SiteCheckerPort siteChecker;

    @Mock
    private SiteRepositoryPort siteRepository;

    @Mock
    private NotificationPort notificationService;

    @Captor
    private ArgumentCaptor<MonitoredSite> siteCaptor;

    @Captor
    private ArgumentCaptor<CheckResult> resultCaptor;

    private MonitoringService monitoringService;

    @BeforeEach
    public void setup() {
        monitoringService = new MonitoringService(siteChecker, siteRepository, notificationService);
    }

    @Test
    public void testAddSite() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .url("https://example.com")
                .name("Example Site")
                .checkInterval(Duration.ofMinutes(5))
                .build();

        when(siteRepository.findByUrl(site.getUrl())).thenReturn(Optional.empty());
        when(siteRepository.save(any(MonitoredSite.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        MonitoredSite result = monitoringService.addSite(site);

        // Assert
        assertNotNull(result);
        assertEquals(site.getUrl(), result.getUrl());
        assertEquals(site.getName(), result.getName());
        assertEquals(MonitoredSite.SiteStatus.UNKNOWN, result.getCurrentStatus());

        verify(siteRepository).findByUrl(site.getUrl());
        verify(siteRepository).save(siteCaptor.capture());
        
        MonitoredSite capturedSite = siteCaptor.getValue();
        assertEquals(MonitoredSite.SiteStatus.UNKNOWN, capturedSite.getCurrentStatus());
    }

    @Test
    public void testAddSiteAlreadyExists() {
        // Arrange
        MonitoredSite existingSite = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Existing Site")
                .build();

        when(siteRepository.findByUrl(existingSite.getUrl())).thenReturn(Optional.of(existingSite));

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            monitoringService.addSite(existingSite);
        });

        assertTrue(exception.getMessage().contains("existe déjà"));
        verify(siteRepository).findByUrl(existingSite.getUrl());
        verify(siteRepository, never()).save(any(MonitoredSite.class));
    }

    @Test
    public void testRemoveSite() {
        // Arrange
        String url = "https://example.com";
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url(url)
                .build();

        when(siteRepository.findByUrl(url)).thenReturn(Optional.of(site));

        // Act
        boolean result = monitoringService.removeSite(url);

        // Assert
        assertTrue(result);
        verify(siteRepository).findByUrl(url);
        verify(siteRepository).delete(site.getId());
    }

    @Test
    public void testRemoveSiteNotFound() {
        // Arrange
        String url = "https://nonexistent.com";
        when(siteRepository.findByUrl(url)).thenReturn(Optional.empty());

        // Act
        boolean result = monitoringService.removeSite(url);

        // Assert
        assertFalse(result);
        verify(siteRepository).findByUrl(url);
        verify(siteRepository, never()).delete(any());
    }

    @Test
    public void testGetAllSites() {
        // Arrange
        List<MonitoredSite> expectedSites = Arrays.asList(
                MonitoredSite.builder().id(1L).url("https://site1.com").build(),
                MonitoredSite.builder().id(2L).url("https://site2.com").build()
        );

        when(siteRepository.findAll()).thenReturn(expectedSites);

        // Act
        List<MonitoredSite> result = monitoringService.getAllSites();

        // Assert
        assertEquals(expectedSites.size(), result.size());
        assertEquals(expectedSites, result);
        verify(siteRepository).findAll();
    }

    @Test
    public void testCheckSiteSuccess() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .notifyOnIssue(true)
                .currentStatus(MonitoredSite.SiteStatus.UNKNOWN)
                .build();

        CheckResult checkResult = CheckResult.builder()
                .statusCode(200)
                .status(CheckResult.CheckStatus.SUCCESS)
                .severity(CheckResult.AlertSeverity.NONE)
                .build();

        when(siteChecker.check(site)).thenReturn(checkResult);

        // Act
        CheckResult result = monitoringService.checkSite(site);

        // Assert
        assertNotNull(result);
        assertEquals(CheckResult.CheckStatus.SUCCESS, result.getStatus());
        
        verify(siteChecker).check(site);
        verify(siteRepository).saveCheckResult(any(CheckResult.class));
        verify(siteRepository).save(siteCaptor.capture());
        
        MonitoredSite capturedSite = siteCaptor.getValue();
        assertEquals(MonitoredSite.SiteStatus.UP, capturedSite.getCurrentStatus());
        
        // No notifications for initial success
        verify(notificationService, never()).sendAlert(any(), any());
        verify(notificationService, never()).sendRecoveryNotification(any(), any());
    }

    @Test
    public void testCheckSiteFailure() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .notifyOnIssue(true)
                .currentStatus(MonitoredSite.SiteStatus.UP)
                .build();

        CheckResult checkResult = CheckResult.builder()
                .statusCode(500)
                .status(CheckResult.CheckStatus.FAILURE)
                .severity(CheckResult.AlertSeverity.HIGH)
                .build();

        when(siteChecker.check(site)).thenReturn(checkResult);

        // Act
        CheckResult result = monitoringService.checkSite(site);

        // Assert
        assertNotNull(result);
        assertEquals(CheckResult.CheckStatus.FAILURE, result.getStatus());
        
        verify(siteChecker).check(site);
        verify(siteRepository).saveCheckResult(any(CheckResult.class));
        verify(siteRepository).save(siteCaptor.capture());
        
        MonitoredSite capturedSite = siteCaptor.getValue();
        assertEquals(MonitoredSite.SiteStatus.DOWN, capturedSite.getCurrentStatus());
        
        // Should send alert
        verify(notificationService).sendAlert(eq(site), any(CheckResult.class));
    }

    @Test
    public void testCheckSiteRecovery() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .notifyOnIssue(true)
                .currentStatus(MonitoredSite.SiteStatus.DOWN)
                .build();

        CheckResult checkResult = CheckResult.builder()
                .statusCode(200)
                .status(CheckResult.CheckStatus.SUCCESS)
                .severity(CheckResult.AlertSeverity.NONE)
                .build();

        when(siteChecker.check(site)).thenReturn(checkResult);

        // Act
        CheckResult result = monitoringService.checkSite(site);

        // Assert
        assertNotNull(result);
        assertEquals(CheckResult.CheckStatus.SUCCESS, result.getStatus());
        
        verify(siteChecker).check(site);
        verify(siteRepository).saveCheckResult(any(CheckResult.class));
        verify(siteRepository).save(siteCaptor.capture());
        
        MonitoredSite capturedSite = siteCaptor.getValue();
        assertEquals(MonitoredSite.SiteStatus.UP, capturedSite.getCurrentStatus());
        
        // Should send recovery notification
        verify(notificationService).sendRecoveryNotification(eq(site), any(CheckResult.class));
    }

    @Test
    public void testCheckSiteInMaintenanceMode() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .maintenanceMode(true)
                .build();

        // Act
        CheckResult result = monitoringService.checkSite(site);

        // Assert
        assertNotNull(result);
        assertEquals(CheckResult.CheckStatus.SUCCESS, result.getStatus());
        assertEquals(CheckResult.AlertSeverity.NONE, result.getSeverity());
        
        // No checker call, no site status update, no notification
        verify(siteChecker, never()).check(any());
        verify(siteRepository, never()).save(any());
        verify(notificationService, never()).sendAlert(any(), any());
        verify(notificationService, never()).sendRecoveryNotification(any(), any());
    }

    @Test
    public void testGenerateReport() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .currentStatus(MonitoredSite.SiteStatus.UP)
                .build();

        List<MonitoredSite> sites = List.of(site);
        when(siteRepository.findAll()).thenReturn(sites);
        
        CheckResult checkResult = CheckResult.builder()
                .site(site)
                .statusCode(200)
                .status(CheckResult.CheckStatus.SUCCESS)
                .timestamp(LocalDateTime.now())
                .responseTime(Duration.ofMillis(150))
                .build();
        
        List<CheckResult> history = List.of(checkResult);
        when(siteRepository.getCheckHistory(eq(1L), any(), any())).thenReturn(history);

        // Act
        String report = monitoringService.generateReport(
                java.time.ZonedDateTime.now().minusDays(1), 
                java.time.ZonedDateTime.now(), 
                "Quotidien");

        // Assert
        assertNotNull(report);
        assertTrue(report.contains("Example Site"));
        assertTrue(report.contains("Rapport Quotidien"));
        assertTrue(report.contains("<!DOCTYPE html>"));
        
        verify(siteRepository).findAll();
        verify(siteRepository).getCheckHistory(eq(1L), any(), any());
    }
}
