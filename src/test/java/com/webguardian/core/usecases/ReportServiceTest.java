package com.webguardian.core.usecases;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.ports.SiteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReportServiceTest {

    @Mock
    private SiteRepositoryPort siteRepository;
    
    private ReportService reportService;
    
    @BeforeEach
    public void setup() {
        reportService = new ReportService(siteRepository);
    }
    
    @Test
    public void testGenerateDailyReport() {
        // Arrange
        MonitoredSite site1 = MonitoredSite.builder()
                .id(1L)
                .name("Site 1")
                .url("https://site1.com")
                .currentStatus(MonitoredSite.SiteStatus.UP)
                .build();
                
        MonitoredSite site2 = MonitoredSite.builder()
                .id(2L)
                .name("Site 2")
                .url("https://site2.com")
                .currentStatus(MonitoredSite.SiteStatus.DOWN)
                .build();
        
        List<MonitoredSite> sites = Arrays.asList(site1, site2);
        when(siteRepository.findAll()).thenReturn(sites);
        
        LocalDateTime now = LocalDateTime.now();
        CheckResult result1 = CheckResult.builder()
                .site(site1)
                .timestamp(now)
                .statusCode(200)
                .status(CheckResult.CheckStatus.SUCCESS)
                .responseTime(Duration.ofMillis(150))
                .build();
                
        CheckResult result2 = CheckResult.builder()
                .site(site2)
                .timestamp(now)
                .statusCode(500)
                .status(CheckResult.CheckStatus.FAILURE)
                .errorMessage("Internal Server Error")
                .build();
        
        List<CheckResult> history1 = List.of(result1);
        List<CheckResult> history2 = List.of(result2);
        
        when(siteRepository.getCheckHistory(eq(1L), any(), any())).thenReturn(history1);
        when(siteRepository.getCheckHistory(eq(2L), any(), any())).thenReturn(history2);
        
        ZonedDateTime start = ZonedDateTime.now().minusDays(1);
        ZonedDateTime end = ZonedDateTime.now();
        
        // Act
        String report = reportService.generateReport(start, end, "Daily");
        
        // Assert
        assertNotNull(report);
        assertTrue(report.contains("<!DOCTYPE html>"));
        assertTrue(report.contains("Daily Report"));
        assertTrue(report.contains("Site 1"));
        assertTrue(report.contains("Site 2"));
        assertTrue(report.contains("UP"));
        assertTrue(report.contains("DOWN"));
        
        verify(siteRepository).findAll();
        verify(siteRepository).getCheckHistory(eq(1L), any(), any());
        verify(siteRepository).getCheckHistory(eq(2L), any(), any());
    }
    
    @Test
    public void testGenerateCustomReport() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .currentStatus(MonitoredSite.SiteStatus.UP)
                .build();
                
        List<MonitoredSite> sites = List.of(site);
        when(siteRepository.findAll()).thenReturn(sites);
        
        LocalDateTime now = LocalDateTime.now();
        CheckResult result1 = CheckResult.builder()
                .site(site)
                .timestamp(now.minusHours(5))
                .statusCode(200)
                .responseTime(Duration.ofMillis(120))
                .status(CheckResult.CheckStatus.SUCCESS)
                .build();
                
        CheckResult result2 = CheckResult.builder()
                .site(site)
                .timestamp(now.minusHours(3))
                .statusCode(500)
                .status(CheckResult.CheckStatus.FAILURE)
                .build();
                
        CheckResult result3 = CheckResult.builder()
                .site(site)
                .timestamp(now)
                .statusCode(200)
                .responseTime(Duration.ofMillis(150))
                .status(CheckResult.CheckStatus.SUCCESS)
                .build();
        
        List<CheckResult> history = Arrays.asList(result1, result2, result3);
        when(siteRepository.getCheckHistory(eq(1L), any(), any())).thenReturn(history);
        
        ZonedDateTime start = ZonedDateTime.now().minusWeeks(1);
        ZonedDateTime end = ZonedDateTime.now();
        
        // Act
        String report = reportService.generateReport(start, end, "Custom");
        
        // Assert
        assertNotNull(report);
        assertTrue(report.contains("<!DOCTYPE html>"));
        assertTrue(report.contains("Custom Report"));
        assertTrue(report.contains("Test Site"));
        assertTrue(report.contains("66.67%")); // 2/3 success rate
        
        verify(siteRepository).findAll();
        verify(siteRepository).getCheckHistory(eq(1L), any(), any());
    }
    
    @Test
    public void testGenerateReportNoData() {
        // Arrange
        when(siteRepository.findAll()).thenReturn(List.of());
        
        ZonedDateTime start = ZonedDateTime.now().minusDays(1);
        ZonedDateTime end = ZonedDateTime.now();
        
        // Act
        String report = reportService.generateReport(start, end, "Weekly");
        
        // Assert
        assertNotNull(report);
        assertTrue(report.contains("<!DOCTYPE html>"));
        assertTrue(report.contains("Weekly Report"));
        assertTrue(report.contains("No sites configured"));
        
        verify(siteRepository).findAll();
    }
    
    @Test
    public void testCalculateAvailability() {
        // Arrange
        CheckResult success1 = CheckResult.builder().status(CheckResult.CheckStatus.SUCCESS).build();
        CheckResult success2 = CheckResult.builder().status(CheckResult.CheckStatus.SUCCESS).build();
        CheckResult failure = CheckResult.builder().status(CheckResult.CheckStatus.FAILURE).build();
        List<CheckResult> history = Arrays.asList(success1, failure, success2);
        
        // Act
        double availability = reportService.calculateAvailability(history);
        
        // Assert
        assertEquals(66.67, availability, 0.01);
    }
    
    @Test
    public void testCalculateAvailabilityNoChecks() {
        // Arrange
        List<CheckResult> history = List.of();
        
        // Act
        double availability = reportService.calculateAvailability(history);
        
        // Assert
        assertEquals(0.0, availability);
    }
    
    @Test
    public void testCalculateAverageResponseTime() {
        // Arrange
        CheckResult check1 = CheckResult.builder()
                .responseTime(Duration.ofMillis(100))
                .status(CheckResult.CheckStatus.SUCCESS)
                .build();
                
        CheckResult check2 = CheckResult.builder()
                .responseTime(Duration.ofMillis(200))
                .status(CheckResult.CheckStatus.SUCCESS)
                .build();
                
        CheckResult check3 = CheckResult.builder()
                .responseTime(Duration.ofMillis(300))
                .status(CheckResult.CheckStatus.SUCCESS)
                .build();
                
        List<CheckResult> history = Arrays.asList(check1, check2, check3);
        
        // Act
        Duration avgTime = reportService.calculateAverageResponseTime(history);
        
        // Assert
        assertEquals(200, avgTime.toMillis());
    }
}
