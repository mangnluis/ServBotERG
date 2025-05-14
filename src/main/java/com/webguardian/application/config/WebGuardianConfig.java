package com.webguardian.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Gestion de la configuration du WebGuardian
 */
@Slf4j
@Data
public class WebGuardianConfig {
    // Discord
    private String discordToken;
    private String discordCommandPrefix = "!";
    private List<String> discordAuthorizedChannels = new ArrayList<>();
    private String discordAlertChannelId;
    private String discordReportChannelId;
    
    // Email
    private String smtpHost;
    private int smtpPort = 587;
    private String smtpUsername;
    private String smtpPassword;
    private String emailFrom;
    private List<String> emailTo = new ArrayList<>();
    private boolean smtpUseSsl = false;
    
    // SMS (Twilio)
    private boolean smsEnabled = false;
    private String twilioAccountSid;
    private String twilioAuthToken;
    private String twilioFromNumber;
    private List<String> smsToNumbers = new ArrayList<>();
    
    // Database
    private String dbUrl = "jdbc:h2:./webguardian";
    private String dbUsername = "sa";
    private String dbPassword = "";
    private int dbPoolSize = 10;
    
    // Monitoring
    private int defaultCheckIntervalMinutes = 5;
    private int defaultTimeoutSeconds = 30;
    private int defaultMaxRetries = 3;
    private int threadPoolSize = 10;
    private boolean checkSslByDefault = true;
    
    private static WebGuardianConfig instance;
    
    /**
     * Charge la configuration depuis un fichier YAML
     */
    public static WebGuardianConfig loadFromFile(String filePath) {
        File configFile = new File(filePath);
        
        // Si le fichier n'existe pas, créer une configuration par défaut
        if (!configFile.exists()) {
            WebGuardianConfig defaultConfig = new WebGuardianConfig();
            defaultConfig.saveToFile(filePath);
            log.info("Configuration par défaut créée: {}", filePath);
            return defaultConfig;
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            instance = mapper.readValue(configFile, WebGuardianConfig.class);
            log.info("Configuration chargée: {}", filePath);
            return instance;
        } catch (IOException e) {
            log.error("Erreur lors du chargement de la configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de charger la configuration", e);
        }
    }
    
    /**
     * Sauvegarde la configuration dans un fichier YAML
     */
    public void saveToFile(String filePath) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.writeValue(new File(filePath), this);
            log.info("Configuration sauvegardée: {}", filePath);
        } catch (IOException e) {
            log.error("Erreur lors de la sauvegarde de la configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de sauvegarder la configuration", e);
        }
    }
    
    /**
     * Charge la configuration depuis un fichier de propriétés
     */
    public static WebGuardianConfig loadFromProperties(String filePath) {
        Properties properties = new Properties();
        File propFile = new File(filePath);
        
        WebGuardianConfig config = new WebGuardianConfig();
        
        if (propFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propFile)) {
                properties.load(fis);
                
                // Discord
                config.setDiscordToken(properties.getProperty("discord.token"));
                config.setDiscordCommandPrefix(getProperty(properties, "discord.prefix", "!"));
                String channels = properties.getProperty("discord.authorized_channels");
                if (channels != null && !channels.isEmpty()) {
                    for (String channel : channels.split(",")) {
                        config.getDiscordAuthorizedChannels().add(channel.trim());
                    }
                }
                config.setDiscordAlertChannelId(properties.getProperty("discord.alert_channel"));
                config.setDiscordReportChannelId(properties.getProperty("discord.report_channel"));
                
                // Email
                config.setSmtpHost(properties.getProperty("email.smtp.host"));
                config.setSmtpPort(Integer.parseInt(getProperty(properties, "email.smtp.port", "587")));
                config.setSmtpUsername(properties.getProperty("email.smtp.username"));
                config.setSmtpPassword(properties.getProperty("email.smtp.password"));
                config.setEmailFrom(properties.getProperty("email.from"));
                String emails = properties.getProperty("email.to");
                if (emails != null && !emails.isEmpty()) {
                    for (String email : emails.split(",")) {
                        config.getEmailTo().add(email.trim());
                    }
                }
                config.setSmtpUseSsl(Boolean.parseBoolean(getProperty(properties, "email.smtp.ssl", "false")));
                
                // Database
                config.setDbUrl(getProperty(properties, "db.url", "jdbc:h2:./webguardian"));
                config.setDbUsername(getProperty(properties, "db.username", "sa"));
                config.setDbPassword(getProperty(properties, "db.password", ""));
                config.setDbPoolSize(Integer.parseInt(getProperty(properties, "db.pool_size", "10")));
                
                // Monitoring
                config.setDefaultCheckIntervalMinutes(Integer.parseInt(
                        getProperty(properties, "monitoring.default_interval", "5")));
                config.setDefaultTimeoutSeconds(Integer.parseInt(
                        getProperty(properties, "monitoring.default_timeout", "30")));
                config.setDefaultMaxRetries(Integer.parseInt(
                        getProperty(properties, "monitoring.default_retries", "3")));
                config.setThreadPoolSize(Integer.parseInt(
                        getProperty(properties, "monitoring.thread_pool", "10")));
                config.setCheckSslByDefault(Boolean.parseBoolean(
                        getProperty(properties, "monitoring.check_ssl", "true")));
                
                log.info("Configuration chargée depuis le fichier de propriétés: {}", filePath);
            } catch (IOException e) {
                log.error("Erreur lors du chargement des propriétés: {}", e.getMessage(), e);
                throw new RuntimeException("Impossible de charger les propriétés", e);
            }
        } else {
            // Créer un fichier de propriétés par défaut
            try (FileOutputStream fos = new FileOutputStream(propFile)) {
                properties.setProperty("discord.token", "YOUR_DISCORD_TOKEN");
                properties.setProperty("discord.prefix", "!");
                properties.setProperty("discord.authorized_channels", "");
                properties.setProperty("discord.alert_channel", "");
                properties.setProperty("discord.report_channel", "");
                
                properties.setProperty("email.smtp.host", "smtp.example.com");
                properties.setProperty("email.smtp.port", "587");
                properties.setProperty("email.smtp.username", "user@example.com");
                properties.setProperty("email.smtp.password", "password");
                properties.setProperty("email.from", "webguardian@example.com");
                properties.setProperty("email.to", "admin@example.com");
                properties.setProperty("email.smtp.ssl", "false");
                
                properties.setProperty("db.url", "jdbc:h2:./webguardian");
                properties.setProperty("db.username", "sa");
                properties.setProperty("db.password", "");
                properties.setProperty("db.pool_size", "10");
                
                properties.setProperty("monitoring.default_interval", "5");
                properties.setProperty("monitoring.default_timeout", "30");
                properties.setProperty("monitoring.default_retries", "3");
                properties.setProperty("monitoring.thread_pool", "10");
                properties.setProperty("monitoring.check_ssl", "true");
                
                properties.store(fos, "WebGuardian Configuration");
                log.info("Fichier de configuration par défaut créé: {}", filePath);
            } catch (IOException e) {
                log.error("Erreur lors de la création du fichier de propriétés: {}", e.getMessage(), e);
            }
        }
        
        instance = config;
        return config;
    }
    
    /**
     * Récupère une propriété avec une valeur par défaut
     */
    private static String getProperty(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
    
    /**
     * Obtient l'instance de configuration
     */
    public static WebGuardianConfig getInstance() {
        if (instance == null) {
            // Cherche d'abord un fichier de configuration YAML
            File yamlFile = new File("config.yml");
            if (yamlFile.exists()) {
                return loadFromFile("config.yml");
            }
            
            // Sinon, cherche un fichier de propriétés
            File propFile = new File("config.properties");
            if (propFile.exists()) {
                return loadFromProperties("config.properties");
            }
            
            // Si aucun fichier n'existe, crée une configuration par défaut
            instance = new WebGuardianConfig();
        }
        
        return instance;
    }
}
