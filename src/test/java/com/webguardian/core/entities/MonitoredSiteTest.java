package com.webguardian.core.entities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;

public class MonitoredSiteTest {

    @Test
    public void testCreateSite() {
        // Arrange & Act
        MonitoredSite site = new MonitoredSite();
        site.setId(1L);
        site.setName("Test Site");
        site.setUrl("https://example.com");
        site.setCheckInterval(Duration.ofMinutes(5));
        site.setResponseTimeThreshold(Duration.ofSeconds(3));
        site.setMaxRetries(3);
        site.setCurrentStatus(MonitoredSite.SiteStatus.UP);
        site.setCheckContent(true);
        site.setContentCheckString("Welcome");
        site.setSslCheck(true);
        site.setNotifyOnIssue(true);
        site.setMaintenanceMode(false);
        site.setCheckHistory(new ArrayList<>());

        // Assert
        assertEquals(1L, site.getId());
        assertEquals("Test Site", site.getName());
        assertEquals("https://example.com", site.getUrl());
        assertEquals(Duration.ofMinutes(5), site.getCheckInterval());
        assertEquals(Duration.ofSeconds(3), site.getResponseTimeThreshold());
        assertEquals(3, site.getMaxRetries());
        assertEquals(MonitoredSite.SiteStatus.UP, site.getCurrentStatus());
        assertTrue(site.isCheckContent());
        assertEquals("Welcome", site.getContentCheckString());
        assertTrue(site.isSslCheck());
        assertTrue(site.isNotifyOnIssue());
        assertFalse(site.isMaintenanceMode());
        assertNotNull(site.getCheckHistory());
        assertTrue(site.getCheckHistory().isEmpty());
    }

    @Test
    public void testBuilderPattern() {
        // Arrange & Act
        MonitoredSite site = MonitoredSite.builder()
                .id(2L)
                .name("Builder Test")
                .url("https://builder-test.com")
                .checkInterval(Duration.ofMinutes(10))
                .responseTimeThreshold(Duration.ofSeconds(5))
                .maxRetries(2)
                .currentStatus(MonitoredSite.SiteStatus.DOWN)
                .checkContent(true)
                .contentCheckString("Error")
                .sslCheck(false)
                .notifyOnIssue(true)
                .maintenanceMode(true)
                .build();

        // Assert
        assertEquals(2L, site.getId());
        assertEquals("Builder Test", site.getName());
        assertEquals("https://builder-test.com", site.getUrl());
        assertEquals(Duration.ofMinutes(10), site.getCheckInterval());
        assertEquals(Duration.ofSeconds(5), site.getResponseTimeThreshold());
        assertEquals(2, site.getMaxRetries());
        assertEquals(MonitoredSite.SiteStatus.DOWN, site.getCurrentStatus());
        assertTrue(site.isCheckContent());
        assertEquals("Error", site.getContentCheckString());
        assertFalse(site.isSslCheck());
        assertTrue(site.isNotifyOnIssue());
        assertTrue(site.isMaintenanceMode());
        assertNotNull(site.getCheckHistory());
    }

    @Test
    public void testEqualsAndHashCode() {
        // Arrange
        MonitoredSite site1 = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .build();

        MonitoredSite site2 = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .build();

        MonitoredSite site3 = MonitoredSite.builder()
                .id(2L)
                .url("https://other.com")
                .build();

        // Assert
        assertEquals(site1, site2);
        assertEquals(site1.hashCode(), site2.hashCode());
        assertNotEquals(site1, site3);
        assertNotEquals(site1.hashCode(), site3.hashCode());
    }
}
