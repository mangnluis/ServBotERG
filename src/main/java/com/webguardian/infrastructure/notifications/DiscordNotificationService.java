package com.webguardian.infrastructure.notifications;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.ports.NotificationPort;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Service de notification via Discord
 */
@Slf4j
public class DiscordNotificationService implements NotificationPort {
    private final JDA jda;
    private final String alertChannelId;
    private final String reportChannelId;
    
    public DiscordNotificationService(JDA jda, String alertChannelId, String reportChannelId) {
        this.jda = jda;
        this.alertChannelId = alertChannelId;
        this.reportChannelId = reportChannelId;
    }
    
    @Override
    public boolean sendAlert(MonitoredSite site, CheckResult checkResult) {
        try {
            TextChannel channel = jda.getTextChannelById(alertChannelId);
            if (channel == null) {
                log.error("Canal d'alerte non trouvé: {}", alertChannelId);
                return false;
            }
            
            MessageEmbed embed = createAlertEmbed(site, checkResult);
            channel.sendMessageEmbeds(embed).queue();
            return true;
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi d'alerte Discord: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean sendRecoveryNotification(MonitoredSite site, CheckResult checkResult) {
        try {
            TextChannel channel = jda.getTextChannelById(alertChannelId);
            if (channel == null) {
                log.error("Canal d'alerte non trouvé: {}", alertChannelId);
                return false;
            }
            
            MessageEmbed embed = createRecoveryEmbed(site, checkResult);
            channel.sendMessageEmbeds(embed).queue();
            return true;
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification de récupération Discord: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean sendReport(String reportContent, String reportType) {
        try {
            TextChannel channel = jda.getTextChannelById(reportChannelId);
            if (channel == null) {
                log.error("Canal de rapport non trouvé: {}", reportChannelId);
                return false;
            }
            
            MessageEmbed embed = new EmbedBuilder()
                    .setTitle("Rapport " + reportType)
                    .setDescription(reportContent)
                    .setColor(Color.BLUE)
                    .setTimestamp(Instant.now())
                    .setFooter("WebGuardian", null)
                    .build();
            
            channel.sendMessageEmbeds(embed).queue();
            return true;
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi du rapport Discord: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Crée un embed pour une alerte
     */
    private MessageEmbed createAlertEmbed(MonitoredSite site, CheckResult checkResult) {
        EmbedBuilder builder = new EmbedBuilder();
        
        Color color;
        String title;
        
        switch (checkResult.getSeverity()) {
            case CRITICAL:
                color = Color.RED;
                title = "⚠️ ALERTE CRITIQUE";
                break;
            case HIGH:
                color = new Color(255, 69, 0); // Orange-rouge
                title = "⚠️ ALERTE HAUTE";
                break;
            case MEDIUM:
                color = Color.ORANGE;
                title = "⚠️ ALERTE MOYENNE";
                break;
            default:
                color = Color.YELLOW;
                title = "⚠️ ALERTE BASSE";
                break;
        }
        
        builder.setTitle(title)
                .setColor(color)
                .setTimestamp(Instant.now())
                .setDescription("Problème détecté sur " + site.getName())
                .addField("URL", site.getUrl(), false);
        
        if (checkResult.getStatusCode() != null) {
            builder.addField("Code de statut", checkResult.getStatusCode().toString(), true);
        }
        
        if (checkResult.getResponseTime() != null) {
            builder.addField("Temps de réponse", formatDuration(checkResult.getResponseTime()), true);
        }
        
        if (!checkResult.isContentCheckPassed()) {
            builder.addField("Vérification de contenu", "❌ Échouée", true);
        }
        
        if (!checkResult.isSslCheckPassed() && site.isSslCheck()) {
            builder.addField("Certificat SSL", "❌ Problème détecté", true);
        }
        
        if (checkResult.getErrorMessage() != null && !checkResult.getErrorMessage().isEmpty()) {
            builder.addField("Erreur", checkResult.getErrorMessage(), false);
        }
        
        builder.setFooter("WebGuardian Monitoring", null);
        
        return builder.build();
    }
    
    /**
     * Crée un embed pour une notification de récupération
     */
    private MessageEmbed createRecoveryEmbed(MonitoredSite site, CheckResult checkResult) {
        return new EmbedBuilder()
                .setTitle("✅ SITE RÉTABLI")
                .setColor(Color.GREEN)
                .setTimestamp(Instant.now())
                .setDescription(site.getName() + " est de nouveau opérationnel")
                .addField("URL", site.getUrl(), false)
                .addField("Code de statut", 
                         checkResult.getStatusCode() != null ? checkResult.getStatusCode().toString() : "N/A", 
                         true)
                .addField("Temps de réponse", 
                         checkResult.getResponseTime() != null ? formatDuration(checkResult.getResponseTime()) : "N/A", 
                         true)
                .setFooter("WebGuardian Monitoring", null)
                .build();
    }
    
    /**
     * Formate une durée en millisecondes
     */
    private String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + " ms";
        } else {
            return (millis / 1000.0) + " s";
        }
    }
}
