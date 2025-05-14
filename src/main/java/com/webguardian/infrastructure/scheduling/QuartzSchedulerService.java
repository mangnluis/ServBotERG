package com.webguardian.infrastructure.scheduling;

import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.usecases.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Service de planification des tâches de vérification à l'aide de Quartz Scheduler
 */
@Slf4j
@RequiredArgsConstructor
public class QuartzSchedulerService {
    private final MonitoringService monitoringService;
    private Scheduler scheduler;
    
    /**
     * Initialise le planificateur Quartz
     */
    public void initialize() throws SchedulerException {
        Properties props = new Properties();
        props.put("org.quartz.threadPool.threadCount", "10");
        props.put("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        
        SchedulerFactory schedulerFactory = new StdSchedulerFactory(props);
        scheduler = schedulerFactory.getScheduler();
        scheduler.start();
        
        log.info("Quartz Scheduler initialisé");
    }
    
    /**
     * Récupère le planificateur Quartz
     * @return Le planificateur
     */
    public Scheduler getScheduler() {
        return scheduler;
    }
    
    /**
     * Planifie la vérification périodique d'un site
     */
    public void scheduleSite(MonitoredSite site) {
        try {
            JobDetail jobDetail = buildJobDetail(site);
            Trigger trigger = buildTrigger(site);
            
            // Si le job existe déjà, on le met à jour
            if (scheduler.checkExists(jobDetail.getKey())) {
                scheduler.deleteJob(jobDetail.getKey());
            }
            
            scheduler.scheduleJob(jobDetail, trigger);
            log.debug("Planification pour le site {} configurée toutes les {} minutes", 
                    site.getUrl(), site.getCheckInterval().toMinutes());
            
        } catch (SchedulerException e) {
            log.error("Erreur lors de la planification du site {}: {}", site.getUrl(), e.getMessage(), e);
        }
    }
    
    /**
     * Planifie tous les sites fournis
     */
    public void scheduleAllSites(List<MonitoredSite> sites) {
        for (MonitoredSite site : sites) {
            scheduleSite(site);
        }
        log.info("{} sites planifiés pour la vérification", sites.size());
    }
    
    /**
     * Annule la planification d'un site
     */
    public void unscheduleSite(MonitoredSite site) {
        try {
            JobKey jobKey = getJobKey(site);
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                log.debug("Planification pour le site {} supprimée", site.getUrl());
            }
        } catch (SchedulerException e) {
            log.error("Erreur lors de la suppression de la planification du site {}: {}", 
                    site.getUrl(), e.getMessage(), e);
        }
    }
    
    /**
     * Déclenche une vérification immédiate d'un site
     */
    public void triggerImmediateCheck(MonitoredSite site) {
        try {
            JobKey jobKey = getJobKey(site);
            if (scheduler.checkExists(jobKey)) {
                scheduler.triggerJob(jobKey);
                log.debug("Vérification immédiate déclenchée pour le site {}", site.getUrl());
            } else {
                // Si le job n'existe pas encore, on le crée et on le déclenche immédiatement
                JobDetail jobDetail = buildJobDetail(site);
                SimpleTrigger trigger = TriggerBuilder.newTrigger()
                        .forJob(jobDetail)
                        .withIdentity("trigger-" + site.getId() + "-immediate")
                        .startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0))
                        .build();
                
                scheduler.scheduleJob(jobDetail, trigger);
            }
        } catch (SchedulerException e) {
            log.error("Erreur lors du déclenchement de la vérification immédiate du site {}: {}", 
                    site.getUrl(), e.getMessage(), e);
        }
    }
    
    /**
     * Arrête le planificateur
     */
    public void shutdown() {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown(true);
                log.info("Quartz Scheduler arrêté");
            }
        } catch (SchedulerException e) {
            log.error("Erreur lors de l'arrêt du planificateur: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Construit le job detail pour un site
     */
    private JobDetail buildJobDetail(MonitoredSite site) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(SiteCheckJob.SITE_ID_KEY, site.getId());
        
        return JobBuilder.newJob(SiteCheckJob.class)
                .withIdentity(getJobKey(site))
                .withDescription("Vérification du site " + site.getUrl())
                .usingJobData(jobDataMap)
                .storeDurably()
                .build();
    }
    
    /**
     * Construit le déclencheur pour un site
     */
    private Trigger buildTrigger(MonitoredSite site) {
        Duration interval = site.getCheckInterval();
        
        // Par défaut 5 minutes si non spécifié
        if (interval == null) {
            interval = Duration.ofMinutes(5);
        }
        
        int seconds = (int) interval.getSeconds();
        if (seconds < 10) {
            seconds = 10; // Minimum 10 secondes entre les vérifications
        }
        
        return TriggerBuilder.newTrigger()
                .forJob(getJobKey(site))
                .withIdentity("trigger-" + site.getId())
                .withDescription("Déclencheur pour " + site.getUrl())
                .startAt(new Date(System.currentTimeMillis() + 5000)) // Démarrage dans 5 secondes
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(seconds)
                        .repeatForever())
                .build();
    }
    
    /**
     * Obtient la clé de job pour un site
     */
    private JobKey getJobKey(MonitoredSite site) {
        return new JobKey("site-check-" + site.getId());
    }
}
