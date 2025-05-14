package com.webguardian.application;

import com.webguardian.application.config.WebGuardianConfig;
import com.webguardian.application.discord.DiscordCommandHandler;
import com.webguardian.application.discord.ReportCommandListener;
import com.webguardian.core.usecases.MonitoringService;
import com.webguardian.core.usecases.ReportService;
import com.webguardian.infrastructure.notifications.DiscordNotificationService;
import com.webguardian.infrastructure.notifications.EmailNotificationService;
import com.webguardian.infrastructure.persistence.H2SiteRepository;
import com.webguardian.infrastructure.scheduling.QuartzSchedulerService;
import com.webguardian.infrastructure.web.OkHttpSiteChecker;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Point d'entrée principal de l'application WebGuardian
 */
@Slf4j
public class WebGuardianApplication {
    public static void main(String[] args) {
        try {
            log.info("Démarrage de WebGuardian...");
            
            // Chargement de la configuration
            String configPath = "config.properties";
            if (args.length > 0) {
                configPath = args[0];
            }
            
            WebGuardianConfig config = WebGuardianConfig.loadFromProperties(configPath);
            log.info("Configuration chargée depuis {}", configPath);
            
            // Création du pool de threads
            ExecutorService executorService = Executors.newFixedThreadPool(
                    config.getThreadPoolSize());
            
            // Initialisation des composants
            OkHttpSiteChecker siteChecker = new OkHttpSiteChecker();
            H2SiteRepository siteRepository = new H2SiteRepository(config.getDbUrl(), 
                    config.getDbUsername(), config.getDbPassword(), config.getDbPoolSize());
            
            // Initialisation de JDA (Discord)
            JDA jda = initializeDiscord(config);
            
            // Services de notification
            DiscordNotificationService discordNotificationService = new DiscordNotificationService(
                    jda,
                    config.getDiscordAlertChannelId(),
                    config.getDiscordReportChannelId());
            
            EmailNotificationService emailNotificationService = new EmailNotificationService(
                    config.getSmtpHost(),
                    config.getSmtpPort(),
                    config.getSmtpUsername(),
                    config.getSmtpPassword(),
                    config.getEmailFrom(),
                    config.getEmailTo(),
                    config.isSmtpUseSsl());
            
            // Services combinés en fonction de la configuration
            MultiChannelNotificationService notificationService = new MultiChannelNotificationService();
            notificationService.addNotificationService(discordNotificationService);
            notificationService.addNotificationService(emailNotificationService);
            
            // Service de monitoring
            MonitoringService monitoringService = new MonitoringService(
                    siteChecker,
                    siteRepository,
                    notificationService);
            
            // Planificateur
            QuartzSchedulerService schedulerService = new QuartzSchedulerService(monitoringService);
            schedulerService.initialize();
            
            // Récupération du planificateur Quartz
            Scheduler scheduler = schedulerService.getScheduler();
            
            // Service de rapport
            ReportService reportService = new ReportService(
                    monitoringService,
                    siteRepository,
                    notificationService);
            
            // Gestionnaire de commandes Discord
            DiscordCommandHandler commandHandler = new DiscordCommandHandler(
                    monitoringService,
                    config.getDiscordAuthorizedChannels(),
                    config.getDiscordCommandPrefix());
            
            // Gestionnaire de commandes de rapport
            ReportCommandListener reportCommandListener = new ReportCommandListener(
                    config.getDiscordAuthorizedChannels(),
                    config.getDiscordCommandPrefix(),
                    reportService);
            
            // Ajout des listeners
            jda.addEventListener(commandHandler);
            jda.addEventListener(reportCommandListener);
            
            // Intégration du service de monitoring dans le planificateur
            try {
                scheduler.getContext().put("monitoringService", monitoringService);
            } catch (SchedulerException e) {
                log.error("Erreur lors de l'ajout du service de monitoring au contexte du planificateur", e);
            }
            
            // Chargement des sites existants dans le planificateur
            schedulerService.scheduleAllSites(siteRepository.findAll());
            
            // Un hook pour l'arrêt propre
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Arrêt de WebGuardian...");
                schedulerService.shutdown();
                executorService.shutdown();
                log.info("Au revoir !");
            }));
            
            log.info("WebGuardian démarré avec succès !");
            
        } catch (Exception e) {
            log.error("Erreur lors du démarrage de WebGuardian: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    /**
     * Initialise JDA (Discord)
     */
    private static JDA initializeDiscord(WebGuardianConfig config) throws Exception {
        try {
            JDA jda = JDABuilder.createDefault(config.getDiscordToken())
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, 
                                  GatewayIntent.MESSAGE_CONTENT,
                                  GatewayIntent.GUILD_MESSAGE_REACTIONS)
                    .build();
            
            jda.awaitReady();
            log.info("Connexion à Discord établie");
            return jda;
        } catch (Exception e) {
            log.error("Erreur lors de la connexion à Discord: {}", e.getMessage(), e);
            throw e;
        }
    }
}
