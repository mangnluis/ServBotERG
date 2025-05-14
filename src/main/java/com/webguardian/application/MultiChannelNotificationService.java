package com.webguardian.application;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.ports.NotificationPort;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service de notification multi-canaux qui distribue les notifications
 * à plusieurs services de notification sous-jacents
 */
@Slf4j
public class MultiChannelNotificationService implements NotificationPort {
    private final List<NotificationPort> notificationServices = new ArrayList<>();
    
    /**
     * Ajoute un service de notification
     * @param notificationService Le service à ajouter
     */
    public void addNotificationService(NotificationPort notificationService) {
        notificationServices.add(notificationService);
        log.info("Service de notification ajouté: {}", notificationService.getClass().getSimpleName());
    }
    
    @Override
    public boolean sendAlert(MonitoredSite site, CheckResult checkResult) {
        log.debug("Envoi d'une alerte pour {} via {} canaux", site.getUrl(), notificationServices.size());
        
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        for (NotificationPort service : notificationServices) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return service.sendAlert(site, checkResult);
                } catch (Exception e) {
                    log.error("Erreur lors de l'envoi d'alerte via {}: {}", 
                            service.getClass().getSimpleName(), e.getMessage(), e);
                    return false;
                }
            }));
        }
        
        // Attendre que toutes les notifications soient envoyées
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Vérifier si au moins un service a réussi
        for (CompletableFuture<Boolean> future : futures) {
            if (future.join()) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean sendRecoveryNotification(MonitoredSite site, CheckResult checkResult) {
        log.debug("Envoi d'une notification de récupération pour {} via {} canaux", 
                site.getUrl(), notificationServices.size());
        
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        for (NotificationPort service : notificationServices) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return service.sendRecoveryNotification(site, checkResult);
                } catch (Exception e) {
                    log.error("Erreur lors de l'envoi de notification de récupération via {}: {}", 
                            service.getClass().getSimpleName(), e.getMessage(), e);
                    return false;
                }
            }));
        }
        
        // Attendre que toutes les notifications soient envoyées
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Vérifier si au moins un service a réussi
        for (CompletableFuture<Boolean> future : futures) {
            if (future.join()) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean sendReport(String reportContent, String reportType) {
        log.debug("Envoi d'un rapport {} via {} canaux", reportType, notificationServices.size());
        
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        for (NotificationPort service : notificationServices) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return service.sendReport(reportContent, reportType);
                } catch (Exception e) {
                    log.error("Erreur lors de l'envoi de rapport via {}: {}", 
                            service.getClass().getSimpleName(), e.getMessage(), e);
                    return false;
                }
            }));
        }
        
        // Attendre que tous les rapports soient envoyés
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // Vérifier si au moins un service a réussi
        for (CompletableFuture<Boolean> future : futures) {
            if (future.join()) {
                return true;
            }
        }
        
        return false;
    }
}
