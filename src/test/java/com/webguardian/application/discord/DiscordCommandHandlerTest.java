package com.webguardian.application.discord;

import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.usecases.MonitoringService;
import com.webguardian.core.usecases.ReportService;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DiscordCommandHandlerTest {

    @Mock
    private MonitoringService monitoringService;
    
    @Mock
    private ReportService reportService;
    
    @Mock
    private MessageReceivedEvent event;
    
    @Mock
    private Message message;
    
    @Mock
    private TextChannel channel;
    
    @Mock
    private MessageCreateAction messageAction;
    
    private DiscordCommandHandler commandHandler;
    private final String prefix = "!wg";
    
    @BeforeEach
    public void setup() {
        commandHandler = new DiscordCommandHandler(monitoringService, reportService, prefix);
        
        // Common setup for event
        when(event.getMessage()).thenReturn(message);
        when(event.getChannel()).thenReturn(channel);
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(messageAction);
        when(messageAction.queue()).thenReturn(null);
    }
    
    @Test
    public void testHandleAddCommand() {
        // Arrange
        when(message.getContentRaw()).thenReturn("!wg add https://example.com Example Site 5m");
        
        MonitoredSite newSite = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .checkInterval(Duration.ofMinutes(5))
                .build();
        
        when(monitoringService.addSite(any(MonitoredSite.class))).thenReturn(newSite);
        
        // Act
        commandHandler.onMessageReceived(event);
        
        // Assert
        ArgumentCaptor<MonitoredSite> siteCaptor = ArgumentCaptor.forClass(MonitoredSite.class);
        verify(monitoringService).addSite(siteCaptor.capture());
        
        MonitoredSite capturedSite = siteCaptor.getValue();
        assertEquals("https://example.com", capturedSite.getUrl());
        assertEquals("Example Site", capturedSite.getName());
        assertEquals(Duration.ofMinutes(5), capturedSite.getCheckInterval());
        
        verify(channel).sendMessage(contains("Site added successfully"));
    }
    
    @Test
    public void testHandleRemoveCommand() {
        // Arrange
        when(message.getContentRaw()).thenReturn("!wg remove https://example.com");
        when(monitoringService.removeSite("https://example.com")).thenReturn(true);
        
        // Act
        commandHandler.onMessageReceived(event);
        
        // Assert
        verify(monitoringService).removeSite("https://example.com");
        verify(channel).sendMessage(contains("Site removed successfully"));
    }
    
    @Test
    public void testHandleRemoveCommandNotFound() {
        // Arrange
        when(message.getContentRaw()).thenReturn("!wg remove https://nonexistent.com");
        when(monitoringService.removeSite("https://nonexistent.com")).thenReturn(false);
        
        // Act
        commandHandler.onMessageReceived(event);
        
        // Assert
        verify(monitoringService).removeSite("https://nonexistent.com");
        verify(channel).sendMessage(contains("Site not found"));
    }
    
    @Test
    public void testHandleListCommand() {
        // Arrange
        when(message.getContentRaw()).thenReturn("!wg list");
        
        List<MonitoredSite> sites = Arrays.asList(
            MonitoredSite.builder().id(1L).url("https://site1.com").name("Site 1").currentStatus(MonitoredSite.SiteStatus.UP).build(),
            MonitoredSite.builder().id(2L).url("https://site2.com").name("Site 2").currentStatus(MonitoredSite.SiteStatus.DOWN).build()
        );
        
        when(monitoringService.getAllSites()).thenReturn(sites);
        
        // Act
        commandHandler.onMessageReceived(event);
        
        // Assert
        verify(monitoringService).getAllSites();
        verify(channel).sendMessageEmbeds(any(MessageEmbed.class));
    }
    
    @Test
    public void testHandleCheckCommand() {
        // Arrange
        when(message.getContentRaw()).thenReturn("!wg check https://example.com");
        
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .build();
        
        when(monitoringService.getSiteByUrl("https://example.com")).thenReturn(Optional.of(site));
        when(monitoringService.checkSite(site)).thenReturn(null); // Actual CheckResult would be returned
        
        // Act
        commandHandler.onMessageReceived(event);
        
        // Assert
        verify(monitoringService).getSiteByUrl("https://example.com");
        verify(monitoringService).checkSite(site);
        verify(channel).sendMessage(contains("Check initiated for"));
    }
    
    @Test
    public void testHandleCheckCommandSiteNotFound() {
        // Arrange
        when(message.getContentRaw()).thenReturn("!wg check https://nonexistent.com");
        when(monitoringService.getSiteByUrl("https://nonexistent.com")).thenReturn(Optional.empty());
        
        // Act
        commandHandler.onMessageReceived(event);
        
        // Assert
        verify(monitoringService).getSiteByUrl("https://nonexistent.com");
        verify(monitoringService, never()).checkSite(any());
        verify(channel).sendMessage(contains("Site not found"));
    }
    
    @Test
    public void testHandleStatusCommand() {
        // Arrange
        when(message.getContentRaw()).thenReturn("!wg status https://example.com");
        
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .url("https://example.com")
                .name("Example Site")
                .currentStatus(MonitoredSite.SiteStatus.UP)
                .build();
        
        when(monitoringService.getSiteByUrl("https://example.com")).thenReturn(Optional.of(site));
        
        // Act
        commandHandler.onMessageReceived(event);
        
        // Assert
        verify(monitoringService).getSiteByUrl("https://example.com");
        verify(channel).sendMessageEmbeds(any(MessageEmbed.class));
    }
    
    @Test
    public void testHandleReportCommand() {
        // Arrange
        when(message.getContentRaw()).thenReturn("!wg report daily");
        
        String reportContent = "<html>Daily Report</html>";
        when(monitoringService.generateReport(any(ZonedDateTime.class), any(ZonedDateTime.class), eq("Quotidien")))
            .thenReturn(reportContent);
        
        // Act
        commandHandler.onMessageReceived(event);
        
        // Assert
        verify(monitoringService).generateReport(any(ZonedDateTime.class), any(ZonedDateTime.class), eq("Quotidien"));
        verify(channel).sendMessage(contains("Report generated successfully"));
    }
    
    @Test
    public void testHandleHelpCommand() {
        // Arrange
        when(message.getContentRaw()).thenReturn("!wg help");
        
        // Act
        commandHandler.onMessageReceived(event);
        
        // Assert
        verify(channel).sendMessageEmbeds(any(MessageEmbed.class));
    }
    
    @Test
    public void testIgnoreNonCommandMessage() {
        // Arrange
        when(message.getContentRaw()).thenReturn("This is not a command");
        
        // Act
        commandHandler.onMessageReceived(event);
        
        // Assert
        verifyNoInteractions(monitoringService);
        verifyNoInteractions(reportService);
        verify(channel, never()).sendMessage(anyString());
        verify(channel, never()).sendMessageEmbeds(any(MessageEmbed.class));
    }
    
    @Test
    public void testHandleInvalidCommand() {
        // Arrange
        when(message.getContentRaw()).thenReturn("!wg invalid command");
        
        // Act
        commandHandler.onMessageReceived(event);
        
        // Assert
        verifyNoInteractions(monitoringService);
        verify(channel).sendMessage(contains("Unknown command"));
    }
}
