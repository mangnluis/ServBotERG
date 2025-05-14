package com.webguardian.infrastructure.scheduling;

import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.usecases.MonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QuartzSchedulerServiceTest {

    @Mock
    private Scheduler scheduler;
    
    @Mock
    private MonitoringService monitoringService;
    
    @Mock
    private SchedulerFactory schedulerFactory;
    
    private QuartzSchedulerService schedulerService;
    
    @BeforeEach
    public void setup() throws SchedulerException {
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        schedulerService = new QuartzSchedulerService(monitoringService, schedulerFactory);
    }
    
    @Test
    public void testInitialize() throws SchedulerException {
        // Act
        schedulerService.initialize();
        
        // Assert
        verify(scheduler).start();
    }
    
    @Test
    public void testScheduleSiteCheck() throws SchedulerException {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .checkInterval(Duration.ofMinutes(5))
                .build();
        
        // Act
        schedulerService.scheduleSiteCheck(site);
        
        // Assert
        verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }
    
    @Test
    public void testUnscheduleSiteCheck() throws SchedulerException {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .build();
        
        String jobKey = "site-check-1";
        
        // Act
        schedulerService.unscheduleSiteCheck(site);
        
        // Assert
        verify(scheduler).deleteJob(any(JobKey.class));
    }
    
    @Test
    public void testUpdateSiteCheckSchedule() throws SchedulerException {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .checkInterval(Duration.ofMinutes(10))
                .build();
        
        // Act
        schedulerService.updateSiteCheckSchedule(site);
        
        // Assert
        verify(scheduler).deleteJob(any(JobKey.class));
        verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }
    
    @Test
    public void testShutdown() throws SchedulerException {
        // Act
        schedulerService.shutdown();
        
        // Assert
        verify(scheduler).shutdown(true);
    }
    
    @Test
    public void testCreateSiteCheckJob() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .build();
        
        // Act
        JobDetail jobDetail = schedulerService.createSiteCheckJob(site);
        
        // Assert
        assertNotNull(jobDetail);
        assertEquals("site-check-1", jobDetail.getKey().getName());
        assertEquals("site-checks", jobDetail.getKey().getGroup());
        
        JobDataMap dataMap = jobDetail.getJobDataMap();
        assertNotNull(dataMap);
        assertEquals(1L, dataMap.getLong("siteId"));
        assertEquals("https://example.com", dataMap.getString("siteUrl"));
    }
    
    @Test
    public void testCreateTrigger() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .checkInterval(Duration.ofMinutes(5))
                .build();
        
        JobDetail jobDetail = JobBuilder.newJob(SiteCheckJob.class)
                .withIdentity("site-check-1", "site-checks")
                .build();
        
        // Act
        Trigger trigger = schedulerService.createTrigger(site, jobDetail);
        
        // Assert
        assertNotNull(trigger);
        assertEquals("trigger-1", trigger.getKey().getName());
        assertEquals("site-triggers", trigger.getKey().getGroup());
        
        // Should be a SimpleTrigger
        assertTrue(trigger instanceof SimpleTrigger);
        SimpleTrigger simpleTrigger = (SimpleTrigger) trigger;
        assertEquals(SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(5)
                .repeatForever()
                .build().getRepeatInterval(),
                simpleTrigger.getRepeatInterval());
    }
}