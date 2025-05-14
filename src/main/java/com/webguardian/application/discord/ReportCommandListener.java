package com.webguardian.application.discord;

import com.webguardian.core.usecases.ReportService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;

import java.util.List;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestionnaire des commandes avancées pour les rapports
 */
@Slf4j
@RequiredArgsConstructor
public class ReportCommandListener extends ListenerAdapter {
    private final List<String> authorizedChannels;
    private final String prefix;
    
    @Getter
    private final ReportService reportService;
    
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore les messages des bots et vérifie si le canal est autorisé
        if (event.getAuthor().isBot() || 
                !authorizedChannels.contains(event.getChannel().getId())) {
            return;
        }
        
        Message message = event.getMessage();
        String content = message.getContentRaw();
        
        // Vérifie si le message commence par le préfixe de commande
        if (!content.startsWith(prefix + "rapport")) {
            return;
        }
        
        String[] parts = content.substring((prefix + "rapport").length()).trim().split("\\s+", 4);
        
        if (parts.length == 0 || parts[0].isEmpty()) {
            sendUsage(event);
            return;
        }
        
        String subCommand = parts[0].toLowerCase();
        
        try {
            switch (subCommand) {
                case "site":
                    if (parts.length >= 2) {
                        handleSiteReport(event, parts);
                    } else {
                        sendUsage(event);
                    }
                    break;
                    
                case "custom":
                    if (parts.length >= 3) {
                        handleCustomReport(event, parts[1], parts[2]);
                    } else {
                        sendCustomReportUsage(event);
                    }
                    break;
                    
                case "quotidien":
                case "hebdomadaire":
                case "mensuel":
                    // Ces commandes sont gérées par DiscordCommandHandler
                    break;
                    
                case "aide":
                case "help":
                    sendReportHelp(event);
                    break;
                    
                default:
                    sendUsage(event);
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement de la commande rapport: {}", e.getMessage(), e);
            event.getChannel().sendMessage("❌ Erreur: " + e.getMessage()).queue();
        }
    }
    
    /**
     * Gère la commande de rapport pour un site spécifique
     */
    private void handleSiteReport(MessageReceivedEvent event, String[] parts) {
        if (parts.length < 2) {
            event.getChannel().sendMessage("❌ Veuillez spécifier l'ID du site").queue();
            return;
        }
        
        try {
            Long siteId = Long.parseLong(parts[1]);
            int days = 7; // Par défaut 7 jours
            
            if (parts.length >= 3) {
                try {
                    days = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    event.getChannel().sendMessage("❌ Nombre de jours invalide, utilisation de la valeur par défaut (7 jours)").queue();
                }
            }
            
            // Message de chargement
            Message loadingMsg = event.getChannel().sendMessage("🔄 Génération du rapport de performance pour le site " + siteId + "...").complete();
            
            // Génération du rapport
            String report = reportService.generateSitePerformanceReport(siteId, days);
            
            // Envoi du rapport
            ByteArrayInputStream inputStream = new ByteArrayInputStream(report.getBytes(StandardCharsets.UTF_8));
            event.getChannel().sendFiles(FileUpload.fromData(inputStream, "rapport_site_" + siteId + ".html")).queue();
            
            loadingMsg.delete().queue();
            
        } catch (NumberFormatException e) {
            event.getChannel().sendMessage("❌ ID de site invalide").queue();
        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport de site: {}", e.getMessage(), e);
            event.getChannel().sendMessage("❌ Erreur lors de la génération du rapport: " + e.getMessage()).queue();
        }
    }
    
    /**
     * Gère la commande de rapport personnalisé
     */
    private void handleCustomReport(MessageReceivedEvent event, String fromStr, String toStr) {
        try {
            LocalDate fromDate;
            LocalDate toDate;
            
            Matcher fromMatcher = DATE_PATTERN.matcher(fromStr);
            Matcher toMatcher = DATE_PATTERN.matcher(toStr);
            
            if (fromMatcher.find() && toMatcher.find()) {
                fromDate = LocalDate.parse(fromMatcher.group(1), DATE_FORMATTER);
                toDate = LocalDate.parse(toMatcher.group(1), DATE_FORMATTER);
            } else {
                event.getChannel().sendMessage("❌ Format de date invalide. Utilisez le format YYYY-MM-DD.").queue();
                return;
            }
            
            if (fromDate.isAfter(toDate)) {
                event.getChannel().sendMessage("❌ La date de début doit être antérieure à la date de fin.").queue();
                return;
            }
            
            // Message de chargement
            Message loadingMsg = event.getChannel().sendMessage("🔄 Génération du rapport personnalisé du " + fromDate + " au " + toDate + "...").complete();
            
            // Génération du rapport
            String report = reportService.generateCustomReport(fromDate, toDate);
            
            // Envoi du rapport
            ByteArrayInputStream inputStream = new ByteArrayInputStream(report.getBytes(StandardCharsets.UTF_8));
            event.getChannel().sendFiles(FileUpload.fromData(inputStream, "rapport_" + fromDate + "_" + toDate + ".html")).queue();
            
            loadingMsg.delete().queue();
            
        } catch (DateTimeParseException e) {
            event.getChannel().sendMessage("❌ Format de date invalide. Utilisez le format YYYY-MM-DD.").queue();
        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport personnalisé: {}", e.getMessage(), e);
            event.getChannel().sendMessage("❌ Erreur lors de la génération du rapport: " + e.getMessage()).queue();
        }
    }
    
    /**
     * Envoie l'aide pour les commandes de rapport
     */
    private void sendReportHelp(MessageReceivedEvent event) {
        event.getChannel().sendMessage(
                "📊 **Aide pour les commandes de rapport**\n\n" +
                        prefix + "rapport quotidien - Génère un rapport pour la journée en cours\n" +
                        prefix + "rapport hebdomadaire - Génère un rapport pour la semaine en cours\n" +
                        prefix + "rapport mensuel - Génère un rapport pour le mois en cours\n" +
                        prefix + "rapport site [id] [jours] - Génère un rapport pour un site spécifique\n" +
                        prefix + "rapport custom [date-début] [date-fin] - Génère un rapport pour une période personnalisée\n\n" +
                        "Les dates doivent être au format YYYY-MM-DD"
        ).queue();
    }
    
    /**
     * Envoie l'usage général des commandes de rapport
     */
    private void sendUsage(MessageReceivedEvent event) {
        event.getChannel().sendMessage(
                "📌 **Usage des commandes de rapport**\n" +
                        prefix + "rapport [quotidien|hebdomadaire|mensuel] - Génère un rapport périodique\n" +
                        prefix + "rapport site [id] [jours] - Génère un rapport pour un site spécifique\n" +
                        prefix + "rapport custom [date-début] [date-fin] - Génère un rapport personnalisé\n" +
                        prefix + "rapport help - Affiche l'aide détaillée pour les rapports"
        ).queue();
    }
    
    /**
     * Envoie l'usage de la commande de rapport personnalisé
     */
    private void sendCustomReportUsage(MessageReceivedEvent event) {
        event.getChannel().sendMessage(
                "📌 **Usage de la commande de rapport personnalisé**\n" +
                        prefix + "rapport custom [date-début] [date-fin]\n\n" +
                        "Les dates doivent être au format YYYY-MM-DD\n" +
                        "Exemple: " + prefix + "rapport custom 2023-01-01 2023-01-31"
        ).queue();
    }
}
