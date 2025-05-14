package com.webguardian.infrastructure.scheduling;

import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.usecases.MonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Optional;

/**
 * Job Quartz qui exécute une vérification de site
 */
@Slf4j
public class SiteCheckJob implements Job {
    public static final String SITE_ID_KEY = "siteId";
    
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        Long siteId = dataMap.getLong(SITE_ID_KEY);
        
        // Récupération du service de monitoring via le context
        MonitoringService monitoringService = (MonitoringService) context.getScheduler().getContext().get("monitoringService");
        
        if (monitoringService == null) {
            log.error("MonitoringService non disponible dans le contexte du planificateur");
            throw new JobExecutionException("MonitoringService non disponible");
        }
        
        try {
            // Récupération du site à vérifier
            Optional<MonitoredSite> siteOpt = monitoringService.getSiteById(siteId);
            
            if (siteOpt.isPresent()) {
                MonitoredSite site = siteOpt.get();
                
                // Si le site est en maintenance, on ignore la vérification
                if (site.isMaintenanceMode()) {
                    log.debug("Site {} en mode maintenance, vérification ignorée", site.getUrl());
                    return;
                }
                
                log.debug("Exécution de la vérification planifiée pour {}", site.getUrl());
                monitoringService.checkSite(site);
            } else {
                log.warn("Site avec ID {} non trouvé, job annulé", siteId);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'exécution de la vérification du site {}: {}", siteId, e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}
