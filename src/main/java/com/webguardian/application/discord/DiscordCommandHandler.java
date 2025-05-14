package com.webguardian.application.discord;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.usecases.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestionnaire des commandes Discord pour WebGuardian
 */
@Slf4j
@RequiredArgsConstructor
public class DiscordCommandHandler extends ListenerAdapter {
    private final MonitoringService monitoringService;
    private final List<String> authorizedChannels;
    private final String prefix;
    
    // Patterns for command parsing
    private static final Pattern URL_PATTERN = 
            Pattern.compile("(https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])");
    private static final Pattern OPTIONS_PATTERN = 
            Pattern.compile("--([a-zA-Z-]+)(=([^ ]+))?");
    
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
        if (!content.startsWith(prefix)) {
            return;
        }
        
        // Extraction des parties de la commande
        String[] parts = content.substring(prefix.length()).trim().split("\\s+", 3);
        if (parts.length == 0) {
            return;
        }
        
        String command = parts[0].toLowerCase();
        
        try {
            switch (command) {
                case "monitor":
                    if (parts.length >= 2) {
                        handleMonitorCommand(event, parts);
                    } else {
                        sendUsage(event);
                    }
                    break;
                    
                case "rapport":
                    if (parts.length >= 2) {
                        handleRapportCommand(event, parts[1]);
                    } else {
                        sendUsage(event);
                    }
                    break;
                    
                case "aide":
                case "help":
                    sendHelp(event);
                    break;
                    
                default:
                    // Commande inconnue
                    event.getChannel().sendMessage("Commande inconnue. Tapez `" + prefix + "help` pour voir la liste des commandes.").queue();
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement de la commande: {}", e.getMessage(), e);
            event.getChannel().sendMessage("❌ Erreur: " + e.getMessage()).queue();
        }
    }
    
    /**
     * Gère les sous-commandes de "monitor"
     */
    private void handleMonitorCommand(MessageReceivedEvent event, String[] parts) {
        String subCommand = parts.length > 1 ? parts[1].toLowerCase() : "";
        
        switch (subCommand) {
            case "add":
                handleAddSite(event, parts.length > 2 ? parts[2] : "");
                break;
                
            case "remove":
                handleRemoveSite(event, parts.length > 2 ? parts[2] : "");
                break;
                
            case "list":
                handleListSites(event);
                break;
                
            case "status":
                handleCheckStatus(event, parts.length > 2 ? parts[2] : "");
                break;
                
            case "config":
                handleConfigSite(event, parts.length > 2 ? parts[2] : "");
                break;
                
            default:
                sendUsage(event);
        }
    }
    
    /**
     * Gère la commande pour ajouter un site à surveiller
     */
    private void handleAddSite(MessageReceivedEvent event, String args) {
        // Extraction de l'URL
        Matcher urlMatcher = URL_PATTERN.matcher(args);
        if (!urlMatcher.find()) {
            event.getChannel().sendMessage("❌ URL invalide ou manquante.").queue();
            return;
        }
        
        String url = urlMatcher.group(1);
        String siteName = extractDomainFromUrl(url);
        
        // Configuration par défaut
        Duration checkInterval = Duration.ofMinutes(5);
        Duration responseTimeThreshold = Duration.ofSeconds(2);
        int maxRetries = 3;
        boolean checkContent = false;
        String contentCheckString = null;
        boolean sslCheck = url.startsWith("https://");
        
        // Extraction des options
        Matcher optionsMatcher = OPTIONS_PATTERN.matcher(args);
        while (optionsMatcher.find()) {
            String option = optionsMatcher.group(1);
            String value = optionsMatcher.group(3);
            
            switch (option) {
                case "name":
                    if (value != null) {
                        siteName = value;
                    }
                    break;
                    
                case "interval":
                    if (value != null) {
                        try {
                            int minutes = Integer.parseInt(value);
                            checkInterval = Duration.ofMinutes(minutes);
                        } catch (NumberFormatException e) {
                            // Ignore invalid value
                        }
                    }
                    break;
                    
                case "timeout":
                    if (value != null) {
                        try {
                            int seconds = Integer.parseInt(value);
                            responseTimeThreshold = Duration.ofSeconds(seconds);
                        } catch (NumberFormatException e) {
                            // Ignore invalid value
                        }
                    }
                    break;
                    
                case "retries":
                    if (value != null) {
                        try {
                            maxRetries = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            // Ignore invalid value
                        }
                    }
                    break;
                    
                case "content-check":
                    checkContent = true;
                    contentCheckString = value;
                    break;
                    
                case "ssl-check":
                    sslCheck = value == null || Boolean.parseBoolean(value);
                    break;
            }
        }
        
        // Création du site à surveiller
        MonitoredSite site = MonitoredSite.builder()
                .name(siteName)
                .url(url)
                .checkInterval(checkInterval)
                .responseTimeThreshold(responseTimeThreshold)
                .maxRetries(maxRetries)
                .checkContent(checkContent)
                .contentCheckString(contentCheckString)
                .sslCheck(sslCheck)
                .notifyOnIssue(true)
                .build();
        
        // Ajout du site via le service
        MonitoredSite addedSite = monitoringService.addSite(site);
        
        // Envoi de la confirmation
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("✅ Site ajouté à la surveillance")
                .setColor(Color.GREEN)
                .setDescription("Le site **" + addedSite.getName() + "** a été ajouté à la surveillance.")
                .addField("URL", addedSite.getUrl(), false)
                .addField("Intervalle de vérification", formatDuration(addedSite.getCheckInterval()), true)
                .addField("Seuil de temps de réponse", formatDuration(addedSite.getResponseTimeThreshold()), true)
                .addField("Tentatives max", String.valueOf(addedSite.getMaxRetries()), true);
        
        if (addedSite.isCheckContent() && addedSite.getContentCheckString() != null) {
            builder.addField("Vérification de contenu", addedSite.getContentCheckString(), false);
        }
        
        builder.addField("Vérification SSL", addedSite.isSslCheck() ? "Activée" : "Désactivée", true);
        
        event.getChannel().sendMessageEmbeds(builder.build()).queue();
    }
    
    /**
     * Gère la commande pour retirer un site de la surveillance
     */
    private void handleRemoveSite(MessageReceivedEvent event, String args) {
        Matcher urlMatcher = URL_PATTERN.matcher(args);
        if (!urlMatcher.find()) {
            event.getChannel().sendMessage("❌ URL invalide ou manquante.").queue();
            return;
        }
        
        String url = urlMatcher.group(1);
        boolean removed = monitoringService.removeSite(url);
        
        if (removed) {
            event.getChannel().sendMessage("✅ Site retiré de la surveillance: " + url).queue();
        } else {
            event.getChannel().sendMessage("❌ Site non trouvé: " + url).queue();
        }
    }
    
    /**
     * Gère la commande pour lister les sites surveillés
     */
    private void handleListSites(MessageReceivedEvent event) {
        List<MonitoredSite> sites = monitoringService.getAllSites();
        
        if (sites.isEmpty()) {
            event.getChannel().sendMessage("Aucun site n'est actuellement surveillé.").queue();
            return;
        }
        
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("📋 Sites surveillés")
                .setColor(new Color(64, 138, 237))
                .setDescription("Liste des sites actuellement surveillés");
        
        for (int i = 0; i < sites.size(); i++) {
            MonitoredSite site = sites.get(i);
            
            // Détermination de l'emoji de statut
            String statusEmoji;
            switch (site.getCurrentStatus()) {
                case UP:
                    statusEmoji = "✅";
                    break;
                case DOWN:
                    statusEmoji = "❌";
                    break;
                case DEGRADED:
                    statusEmoji = "⚠️";
                    break;
                case MAINTENANCE:
                    statusEmoji = "🔧";
                    break;
                default:
                    statusEmoji = "❓";
            }
            
            String siteInfo = String.format("%s **%s** - %s", 
                    statusEmoji, 
                    site.getName(), 
                    formatDuration(site.getCheckInterval()));
            
            if (site.isMaintenanceMode()) {
                siteInfo += " (maintenance)";
            }
            
            builder.addField(site.getUrl(), siteInfo, false);
            
            // Discord limite les embeds à 25 champs
            if (i >= 24 && sites.size() > 25) {
                builder.setFooter("Affichage limité à 25 sites sur " + sites.size());
                break;
            }
        }
        
        event.getChannel().sendMessageEmbeds(builder.build()).queue();
    }
    
    /**
     * Gère la commande pour vérifier le statut d'un site
     */
    private void handleCheckStatus(MessageReceivedEvent event, String args) {
        Matcher urlMatcher = URL_PATTERN.matcher(args);
        if (!urlMatcher.find()) {
            event.getChannel().sendMessage("❌ URL invalide ou manquante.").queue();
            return;
        }
        
        String url = urlMatcher.group(1);
        
        // Message de chargement
        Message loadingMsg = event.getChannel().sendMessage("🔄 Vérification du site " + url + "...").complete();
        
        Optional<CheckResult> resultOpt = monitoringService.checkSiteNow(url);
        
        if (resultOpt.isEmpty()) {
            loadingMsg.editMessage("❌ Site non trouvé dans la liste de surveillance: " + url).queue();
            return;
        }
        
        CheckResult result = resultOpt.get();
        MonitoredSite site = result.getSite();
        
        Color color;
        String statusText;
        
        switch (result.getStatus()) {
            case SUCCESS:
                color = Color.GREEN;
                statusText = "✅ OPÉRATIONNEL";
                break;
            case FAILURE:
                color = Color.RED;
                statusText = "❌ EN ERREUR";
                break;
            case TIMEOUT:
                color = Color.ORANGE;
                statusText = "⚠️ TEMPS DE RÉPONSE EXCESSIF";
                break;
            default:
                color = Color.GRAY;
                statusText = "❓ STATUT INCONNU";
        }
        
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(statusText)
                .setColor(color)
                .setDescription("Statut actuel de " + site.getName())
                .addField("URL", site.getUrl(), false)
                .setTimestamp(Instant.now());
        
        if (result.getStatusCode() != null) {
            builder.addField("Code HTTP", result.getStatusCode().toString(), true);
        }
        
        if (result.getResponseTime() != null) {
            builder.addField("Temps de réponse", formatDuration(result.getResponseTime()), true);
        }
        
        builder.addField("Taille de la réponse", formatSize(result.getContentSize()), true);
        
        if (site.isCheckContent()) {
            String contentStatus = result.isContentCheckPassed() ? "✅ OK" : "❌ Échec";
            builder.addField("Vérification de contenu", contentStatus, true);
        }
        
        if (site.isSslCheck()) {
            String sslStatus = result.isSslCheckPassed() ? "✅ Valide" : "❌ Problème détecté";
            builder.addField("Certificat SSL", sslStatus, true);
        }
        
        if (result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()) {
            builder.addField("Erreur", result.getErrorMessage(), false);
        }
        
        loadingMsg.delete().queue();
        event.getChannel().sendMessageEmbeds(builder.build()).queue();
    }
    
    /**
     * Gère la commande pour configurer un site
     */
    private void handleConfigSite(MessageReceivedEvent event, String args) {
        // Extraction de l'URL
        Matcher urlMatcher = URL_PATTERN.matcher(args);
        if (!urlMatcher.find()) {
            event.getChannel().sendMessage("❌ URL invalide ou manquante.").queue();
            return;
        }
        
        String url = urlMatcher.group(1);
        
        // Recherche du site
        Optional<MonitoredSite> siteOpt = monitoringService.getAllSites().stream()
                .filter(s -> s.getUrl().equals(url))
                .findFirst();
        
        if (siteOpt.isEmpty()) {
            event.getChannel().sendMessage("❌ Site non trouvé dans la liste de surveillance: " + url).queue();
            return;
        }
        
        MonitoredSite site = siteOpt.get();
        boolean updated = false;
        
        // Extraction des options
        Matcher optionsMatcher = OPTIONS_PATTERN.matcher(args);
        while (optionsMatcher.find()) {
            String option = optionsMatcher.group(1);
            String value = optionsMatcher.group(3);
            
            updated = true;
            
            switch (option) {
                case "name":
                    if (value != null) {
                        site.setName(value);
                    }
                    break;
                    
                case "interval":
                    if (value != null) {
                        try {
                            int minutes = Integer.parseInt(value);
                            site.setCheckInterval(Duration.ofMinutes(minutes));
                        } catch (NumberFormatException e) {
                            event.getChannel().sendMessage("❌ Valeur d'intervalle invalide: " + value).queue();
                            updated = false;
                        }
                    }
                    break;
                    
                case "timeout":
                    if (value != null) {
                        try {
                            int seconds = Integer.parseInt(value);
                            site.setResponseTimeThreshold(Duration.ofSeconds(seconds));
                        } catch (NumberFormatException e) {
                            event.getChannel().sendMessage("❌ Valeur de timeout invalide: " + value).queue();
                            updated = false;
                        }
                    }
                    break;
                    
                case "retries":
                    if (value != null) {
                        try {
                            int retries = Integer.parseInt(value);
                            site.setMaxRetries(retries);
                        } catch (NumberFormatException e) {
                            event.getChannel().sendMessage("❌ Valeur de retries invalide: " + value).queue();
                            updated = false;
                        }
                    }
                    break;
                    
                case "content-check":
                    site.setCheckContent(true);
                    site.setContentCheckString(value);
                    break;
                    
                case "ssl-check":
                    site.setSslCheck(value == null || Boolean.parseBoolean(value));
                    break;
                    
                case "maintenance":
                    boolean maintenance = value == null || Boolean.parseBoolean(value);
                    monitoringService.setMaintenanceMode(url, maintenance);
                    event.getChannel().sendMessage(maintenance ? 
                            "🔧 Mode maintenance activé pour " + site.getName() :
                            "✅ Mode maintenance désactivé pour " + site.getName()).queue();
                    return;
                    
                default:
                    event.getChannel().sendMessage("⚠️ Option inconnue: " + option).queue();
                    updated = false;
            }
        }
        
        if (updated) {
            monitoringService.addSite(site); // Réutilisation de la méthode pour sauvegarder les modifications
            event.getChannel().sendMessage("✅ Configuration mise à jour pour " + site.getName()).queue();
        } else {
            sendConfigUsage(event);
        }
    }
    
    /**
     * Gère la commande pour générer un rapport
     */
    private void handleRapportCommand(MessageReceivedEvent event, String type) {
        // Vérification du service de rapport
        if (!(event.getJDA().getRegisteredListeners().stream()
                .anyMatch(listener -> listener instanceof ReportCommandListener))) {
            event.getChannel().sendMessage("❌ Service de rapport non disponible").queue();
            return;
        }

        ReportCommandListener reportListener = (ReportCommandListener) event.getJDA().getRegisteredListeners().stream()
                .filter(listener -> listener instanceof ReportCommandListener)
                .findFirst()
                .get();

        // Message de chargement
        Message loadingMsg = event.getChannel().sendMessage("🔄 Génération du rapport " + type + " en cours...").complete();

        try {
            boolean success = false;
            switch (type.toLowerCase()) {
                case "quotidien":
                    success = reportListener.getReportService().generateAndSendDailyReport();
                    loadingMsg.editMessage("✅ Rapport quotidien généré et envoyé avec succès").queue();
                    break;
                    
                case "hebdomadaire":
                    success = reportListener.getReportService().generateAndSendWeeklyReport();
                    loadingMsg.editMessage("✅ Rapport hebdomadaire généré et envoyé avec succès").queue();
                    break;
                    
                case "mensuel":
                    success = reportListener.getReportService().generateAndSendMonthlyReport();
                    loadingMsg.editMessage("✅ Rapport mensuel généré et envoyé avec succès").queue();
                    break;
                    
                default:
                    loadingMsg.editMessage("❌ Type de rapport inconnu: " + type + ". Types disponibles: quotidien, hebdomadaire, mensuel.").queue();
                    return;
            }
        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport {}: {}", type, e.getMessage(), e);
            loadingMsg.editMessage("❌ Erreur lors de la génération du rapport: " + e.getMessage()).queue();
        }
    }
    
    /**
     * Envoie l'aide pour les commandes
     */
    private void sendHelp(MessageReceivedEvent event) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("📚 Aide WebGuardian")
                .setColor(new Color(75, 0, 130))
                .setDescription("Commandes disponibles pour WebGuardian")
                .addField(prefix + "monitor add [url] [options]", 
                        "Ajoute un site à surveiller\nOptions: --name=nom --interval=min --timeout=sec --retries=n --content-check=texte --ssl-check=true/false", false)
                .addField(prefix + "monitor remove [url]", 
                        "Retire un site de la surveillance", false)
                .addField(prefix + "monitor list", 
                        "Liste tous les sites surveillés", false)
                .addField(prefix + "monitor status [url]", 
                        "Vérifie immédiatement l'état d'un site", false)
                .addField(prefix + "monitor config [url] [options]", 
                        "Configure les paramètres d'un site\nOptions: --name=nom --interval=min --timeout=sec --retries=n --content-check=texte --ssl-check=true/false --maintenance=true/false", false)
                .addField(prefix + "rapport [quotidien/hebdomadaire]", 
                        "Génère un rapport de performance", false)
                .addField(prefix + "help", 
                        "Affiche cette aide", false)
                .setFooter("WebGuardian v1.0", null);
        
        event.getChannel().sendMessageEmbeds(builder.build()).queue();
    }
    
    /**
     * Envoie l'usage général des commandes
     */
    private void sendUsage(MessageReceivedEvent event) {
        event.getChannel().sendMessage(
                "📌 **Commandes disponibles:**\n" +
                        prefix + "monitor add [url] [options] - Ajoute un site à surveiller\n" +
                        prefix + "monitor remove [url] - Retire un site de la surveillance\n" +
                        prefix + "monitor list - Liste tous les sites surveillés\n" +
                        prefix + "monitor status [url] - Vérifie immédiatement l'état d'un site\n" +
                        prefix + "monitor config [url] [options] - Configure les paramètres d'un site\n" +
                        prefix + "rapport [quotidien/hebdomadaire] - Génère un rapport de performance\n" +
                        prefix + "help - Affiche l'aide complète"
        ).queue();
    }
    
    /**
     * Envoie l'usage de la commande config
     */
    private void sendConfigUsage(MessageReceivedEvent event) {
        event.getChannel().sendMessage(
                "📌 **Usage de la commande config:**\n" +
                        prefix + "monitor config [url] --option=valeur\n\n" +
                        "Options disponibles:\n" +
                        "--name=nom - Nom du site\n" +
                        "--interval=minutes - Intervalle de vérification en minutes\n" +
                        "--timeout=secondes - Seuil de temps de réponse en secondes\n" +
                        "--retries=nombre - Nombre de tentatives avant alerte\n" +
                        "--content-check=texte - Texte à vérifier dans la page\n" +
                        "--ssl-check=true/false - Activer/désactiver la vérification SSL\n" +
                        "--maintenance=true/false - Activer/désactiver le mode maintenance"
        ).queue();
    }
    
    /**
     * Extrait le nom de domaine d'une URL
     */
    private String extractDomainFromUrl(String url) {
        try {
            String domain = url.replaceAll("https?://", "")
                    .replaceAll("www\\.", "")
                    .split("/")[0];
            return domain;
        } catch (Exception e) {
            return url; // Fallback
        }
    }
    
    /**
     * Formate une durée en texte lisible
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        
        if (seconds < 60) {
            return seconds + " secondes";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes";
        } else {
            return (seconds / 3600) + " heures";
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
