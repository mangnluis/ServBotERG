package com.webguardian.infrastructure.notifications;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.ports.NotificationPort;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;

/**
 * Service de notification par email
 */
@Slf4j
public class EmailNotificationService implements NotificationPort {
    private final Session session;
    private final String fromEmail;
    private final List<String> toEmails;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public EmailNotificationService(String smtpHost, int smtpPort, String username, String password, 
                                   String fromEmail, List<String> toEmails, boolean useSsl) {
        this.fromEmail = fromEmail;
        this.toEmails = toEmails;
        
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        
        if (useSsl) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        
        session = Session.getInstance(props, new jakarta.mail.Authenticator() {
            protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                return new jakarta.mail.PasswordAuthentication(username, password);
            }
        });
    }
    
    @Override
    public boolean sendAlert(MonitoredSite site, CheckResult checkResult) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            
            for (String email : toEmails) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(email));
            }
            
            String subject = String.format("[ALERTE] Problème détecté sur %s", site.getName());
            message.setSubject(subject);
            
            String content = createAlertEmailContent(site, checkResult);
            message.setContent(content, "text/html; charset=utf-8");
            
            Transport.send(message);
            log.info("Alerte email envoyée pour {}", site.getUrl());
            return true;
        } catch (MessagingException e) {
            log.error("Erreur lors de l'envoi d'email d'alerte: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean sendRecoveryNotification(MonitoredSite site, CheckResult checkResult) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            
            for (String email : toEmails) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(email));
            }
            
            String subject = String.format("[RÉCUPÉRATION] Site rétabli: %s", site.getName());
            message.setSubject(subject);
            
            String content = createRecoveryEmailContent(site, checkResult);
            message.setContent(content, "text/html; charset=utf-8");
            
            Transport.send(message);
            log.info("Notification de récupération email envoyée pour {}", site.getUrl());
            return true;
        } catch (MessagingException e) {
            log.error("Erreur lors de l'envoi d'email de récupération: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean sendReport(String reportContent, String reportType) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            
            for (String email : toEmails) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(email));
            }
            
            String subject = String.format("[RAPPORT %s] WebGuardian", reportType.toUpperCase());
            message.setSubject(subject);
            
            message.setContent(reportContent, "text/html; charset=utf-8");
            
            Transport.send(message);
            log.info("Rapport email {} envoyé", reportType);
            return true;
        } catch (MessagingException e) {
            log.error("Erreur lors de l'envoi du rapport email: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Crée le contenu HTML d'un email d'alerte
     */
    private String createAlertEmailContent(MonitoredSite site, CheckResult checkResult) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><style>");
        html.append("body { font-family: Arial, sans-serif; }");
        html.append(".alert { background-color: #ffebee; border-left: 5px solid #f44336; padding: 15px; }");
        html.append(".info { margin-top: 20px; background-color: #f5f5f5; padding: 15px; }");
        html.append("table { border-collapse: collapse; width: 100%; }");
        html.append("th, td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }");
        html.append("</style></head><body>");
        
        html.append("<h2>Alerte WebGuardian</h2>");
        html.append("<div class='alert'>");
        html.append("<h3>Problème détecté sur ").append(site.getName()).append("</h3>");
        html.append("<p>URL: <a href='").append(site.getUrl()).append("'>").append(site.getUrl()).append("</a></p>");
        html.append("<p>Date: ").append(checkResult.getTimestamp().format(DATE_FORMATTER)).append("</p>");
        html.append("</div>");
        
        html.append("<div class='info'>");
        html.append("<h3>Détails</h3>");
        html.append("<table>");
        
        html.append("<tr><th>Statut</th><td>").append(checkResult.getStatus()).append("</td></tr>");
        if (checkResult.getStatusCode() != null) {
            html.append("<tr><th>Code HTTP</th><td>").append(checkResult.getStatusCode()).append("</td></tr>");
        }
        if (checkResult.getResponseTime() != null) {
            html.append("<tr><th>Temps de réponse</th><td>").append(formatDuration(checkResult.getResponseTime())).append("</td></tr>");
        }
        html.append("<tr><th>Taille de la réponse</th><td>").append(formatSize(checkResult.getContentSize())).append("</td></tr>");
        html.append("<tr><th>Sévérité</th><td>").append(checkResult.getSeverity()).append("</td></tr>");
        
        if (checkResult.getErrorMessage() != null && !checkResult.getErrorMessage().isEmpty()) {
            html.append("<tr><th>Message d'erreur</th><td>").append(checkResult.getErrorMessage()).append("</td></tr>");
        }
        
        html.append("</table>");
        html.append("</div>");
        
        html.append("<p style='margin-top: 30px; font-size: 12px; color: #666;'>Ce message a été envoyé automatiquement par WebGuardian.</p>");
        html.append("</body></html>");
        
        return html.toString();
    }
    
    /**
     * Crée le contenu HTML d'un email de récupération
     */
    private String createRecoveryEmailContent(MonitoredSite site, CheckResult checkResult) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><style>");
        html.append("body { font-family: Arial, sans-serif; }");
        html.append(".recovery { background-color: #e8f5e9; border-left: 5px solid #4caf50; padding: 15px; }");
        html.append(".info { margin-top: 20px; background-color: #f5f5f5; padding: 15px; }");
        html.append("table { border-collapse: collapse; width: 100%; }");
        html.append("th, td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }");
        html.append("</style></head><body>");
        
        html.append("<h2>Notification de récupération WebGuardian</h2>");
        html.append("<div class='recovery'>");
        html.append("<h3>Site rétabli: ").append(site.getName()).append("</h3>");
        html.append("<p>URL: <a href='").append(site.getUrl()).append("'>").append(site.getUrl()).append("</a></p>");
        html.append("<p>Date: ").append(checkResult.getTimestamp().format(DATE_FORMATTER)).append("</p>");
        html.append("</div>");
        
        html.append("<div class='info'>");
        html.append("<h3>Détails</h3>");
        html.append("<table>");
        
        if (checkResult.getStatusCode() != null) {
            html.append("<tr><th>Code HTTP</th><td>").append(checkResult.getStatusCode()).append("</td></tr>");
        }
        if (checkResult.getResponseTime() != null) {
            html.append("<tr><th>Temps de réponse</th><td>").append(formatDuration(checkResult.getResponseTime())).append("</td></tr>");
        }
        
        html.append("</table>");
        html.append("</div>");
        
        html.append("<p style='margin-top: 30px; font-size: 12px; color: #666;'>Ce message a été envoyé automatiquement par WebGuardian.</p>");
        html.append("</body></html>");
        
        return html.toString();
    }
    
    /**
     * Formate une durée en texte lisible
     */
    private String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + " ms";
        } else {
            return String.format("%.2f s", millis / 1000.0);
        }
    }
    
    /**
     * Formate une taille en bytes de façon lisible
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
