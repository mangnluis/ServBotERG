package com.webguardian.infrastructure.notifications;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailNotificationServiceTest {

    @Mock
    private Session session;
    
    @Mock
    private Transport transport;
    
    @Captor
    private ArgumentCaptor<MimeMessage> messageCaptor;
    
    private EmailNotificationService emailService;
    private final String smtpHost = "smtp.example.com";
    private final String smtpPort = "587";
    private final String fromEmail = "alerts@webguardian.com";
    private final String toEmail = "admin@example.com";
    private final String password = "password123";
    
    @BeforeEach
    public void setup() throws Exception {
        // Create a service with dependency injection for testing
        emailService = new EmailNotificationService(smtpHost, smtpPort, fromEmail, toEmail, password) {
            @Override
            protected Session createSession(Properties props) {
                return session;
            }
            
            @Override
            protected Transport getTransport() throws MessagingException {
                return transport;
            }
        };
    }
    
    @Test
    public void testSendAlert() throws MessagingException {
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
        
        doNothing().when(transport).connect(anyString(), anyString(), anyString());
        doNothing().when(transport).sendMessage(any(MimeMessage.class), any());
        
        // Act
        emailService.sendAlert(site, result);
        
        // Assert
        verify(transport).connect(smtpHost, fromEmail, password);
        verify(transport).sendMessage(messageCaptor.capture(), any());
        verify(transport).close();
        
        MimeMessage message = messageCaptor.getValue();
        assertNotNull(message);
    }
    
    @Test
    public void testSendRecoveryNotification() throws MessagingException {
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
        
        doNothing().when(transport).connect(anyString(), anyString(), anyString());
        doNothing().when(transport).sendMessage(any(MimeMessage.class), any());
        
        // Act
        emailService.sendRecoveryNotification(site, result);
        
        // Assert
        verify(transport).connect(smtpHost, fromEmail, password);
        verify(transport).sendMessage(messageCaptor.capture(), any());
        verify(transport).close();
        
        MimeMessage message = messageCaptor.getValue();
        assertNotNull(message);
    }
    
    @Test
    public void testSendReport() throws MessagingException {
        // Arrange
        String report = "This is a test report";
        
        doNothing().when(transport).connect(anyString(), anyString(), anyString());
        doNothing().when(transport).sendMessage(any(MimeMessage.class), any());
        
        // Act
        emailService.sendReport(report);
        
        // Assert
        verify(transport).connect(smtpHost, fromEmail, password);
        verify(transport).sendMessage(messageCaptor.capture(), any());
        verify(transport).close();
        
        MimeMessage message = messageCaptor.getValue();
        assertNotNull(message);
    }
    
    @Test
    public void testHandleMessagingException() throws MessagingException {
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
        
        doThrow(new MessagingException("Authentication failed"))
            .when(transport).connect(anyString(), anyString(), anyString());
        
        // Act & Assert
        assertDoesNotThrow(() -> emailService.sendAlert(site, result));
        
        // Verify attempt was made
        verify(transport).connect(smtpHost, fromEmail, password);
        verify(transport, never()).sendMessage(any(), any());
    }
}