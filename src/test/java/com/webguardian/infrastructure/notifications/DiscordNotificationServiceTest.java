package com.webguardian.infrastructure.notifications;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DiscordNotificationServiceTest {

    @Mock
    private JDA jda;
    
    @Mock
    private TextChannel channel;
    
    @Mock
    private MessageCreateAction messageAction;
    
    private DiscordNotificationService discordService;
    private final String channelId = "123456789";
    
    @BeforeEach
    public void setup() {
        discordService = new DiscordNotificationService(jda, channelId);
    }
    
    @Test
    public void testSendAlert() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .build();
                
        CheckResult result = CheckResult.builder()
                .id(1L)
                .site(site)
                .timestamp(LocalDateTime.now())
                .statusCode(500)
                .status(CheckResult.CheckStatus.FAILURE)
                .errorMessage("Internal Server Error")
                .severity(CheckResult.AlertSeverity.HIGH)
                .build();
        
        when(jda.getTextChannelById(channelId)).thenReturn(channel);
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.queue()).thenReturn(null);
        
        // Act
        discordService.sendAlert(site, result);
        
        // Assert
        verify(jda).getTextChannelById(channelId);
        verify(channel).sendMessage(contains("ALERT"));
        verify(channel).sendMessage(contains("Test Site"));
        verify(channel).sendMessage(contains("https://example.com"));
        verify(channel).sendMessage(contains("Internal Server Error"));
        verify(messageAction).queue();
    }
    
    @Test
    public void testSendRecoveryNotification() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .build();
                
        CheckResult result = CheckResult.builder()
                .id(1L)
                .site(site)
                .timestamp(LocalDateTime.now())
                .statusCode(200)
                .responseTime(Duration.ofMillis(150))
                .status(CheckResult.CheckStatus.SUCCESS)
                .build();
        
        when(jda.getTextChannelById(channelId)).thenReturn(channel);
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.queue()).thenReturn(null);
        
        // Act
        discordService.sendRecoveryNotification(site, result);
        
        // Assert
        verify(jda).getTextChannelById(channelId);
        verify(channel).sendMessage(contains("RECOVERED"));
        verify(channel).sendMessage(contains("Test Site"));
        verify(channel).sendMessage(contains("https://example.com"));
        verify(messageAction).queue();
    }
    
    @Test
    public void testChannelNotFound() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .build();
                
        CheckResult result = CheckResult.builder()
                .id(1L)
                .site(site)
                .timestamp(LocalDateTime.now())
                .statusCode(500)
                .status(CheckResult.CheckStatus.FAILURE)
                .build();
        
        when(jda.getTextChannelById(channelId)).thenReturn(null);
        
        // Act & Assert
        assertDoesNotThrow(() -> discordService.sendAlert(site, result));
        
        // No message sent, just graceful failure
        verify(jda).getTextChannelById(channelId);
        verifyNoMoreInteractions(jda);
    }
    
    @Test
    public void testDiscordException() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url("https://example.com")
                .build();
                
        CheckResult result = CheckResult.builder()
                .id(1L)
                .site(site)
                .timestamp(LocalDateTime.now())
                .statusCode(500)
                .status(CheckResult.CheckStatus.FAILURE)
                .build();
        
        when(jda.getTextChannelById(channelId)).thenReturn(channel);
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.queue()).thenThrow(new RuntimeException("Discord API error"));
        
        // Act & Assert
        assertDoesNotThrow(() -> discordService.sendAlert(site, result));
        
        // Verify attempt was made
        verify(jda).getTextChannelById(channelId);
        verify(channel).sendMessage(anyString());
        verify(messageAction).queue();
    }
    
    @Test
    public void testSendReport() {
        // Arrange
        String report = "This is a test report";
        
        when(jda.getTextChannelById(channelId)).thenReturn(channel);
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.queue()).thenReturn(null);
        
        // Act
        discordService.sendReport(report);
        
        // Assert
        verify(jda).getTextChannelById(channelId);
        verify(channel).sendMessage(contains("REPORT"));
        verify(channel).sendMessage(contains(report));
        verify(messageAction).queue();
    }
}