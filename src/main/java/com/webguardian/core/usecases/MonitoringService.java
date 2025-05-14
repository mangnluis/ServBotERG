package com.webguardian.core.usecases;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.ports.NotificationPort;
import com.webguardian.core.ports.SiteCheckerPort;
import com.webguardian.core.ports.SiteRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service principal pour la surveillance des sites
 */
@Slf4j
@RequiredArgsConstructor
public class MonitoringService {
    private final SiteCheckerPort siteChecker;
    private final SiteRepositoryPort siteRepository;
    private final NotificationPort notificationService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    /**
     * Ajoute un nouveau site à surveiller
     * @param site Le site à ajouter
     * @return Le site ajouté avec son ID
     */
    public MonitoredSite addSite(MonitoredSite site) {
        Optional<MonitoredSite> existing = siteRepository.findByUrl(site.getUrl());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Un site avec cette URL existe déjà: " + site.getUrl());
        }
        
        site.setCurrentStatus(MonitoredSite.SiteStatus.UNKNOWN);
        MonitoredSite savedSite = siteRepository.save(site);
        
        // Effectue une première vérification
        CompletableFuture.runAsync(() -> checkSite(savedSite), executorService);
        
        return savedSite;
    }
    
    /**
     * Supprime un site de la surveillance
     * @param url L'URL du site à supprimer
     * @return true si le site a été supprimé, false sinon
     */
    public boolean removeSite(String url) {
        Optional<MonitoredSite> site = siteRepository.findByUrl(url);
        if (site.isEmpty()) {
            return false;
        }
        
        siteRepository.delete(site.get().getId());
        return true;
    }
    
    /**
     * Récupère tous les sites surveillés
     * @return La liste des sites
     */
    public List<MonitoredSite> getAllSites() {
        return siteRepository.findAll();
    }
    
    /**
     * Vérifie immédiatement un site
     * @param url L'URL du site à vérifier
     * @return Le résultat de la vérification, ou empty si le site n'existe pas
     */
    public Optional<CheckResult> checkSiteNow(String url) {
        Optional<MonitoredSite> site = siteRepository.findByUrl(url);
        if (site.isEmpty()) {
            return Optional.empty();
        }
        
        return Optional.of(checkSite(site.get()));
    }
    
    /**
     * Effectue une vérification d'un site et gère les alertes si nécessaire
     * @param site Le site à vérifier
     * @return Le résultat de la vérification
     */
    public CheckResult checkSite(MonitoredSite site) {
        if (site.isMaintenanceMode()) {
            log.info("Site {} en maintenance, vérification ignorée", site.getUrl());
            return CheckResult.builder()
                    .site(site)
                    .timestamp(LocalDateTime.now())
                    .status(CheckResult.CheckStatus.SUCCESS)
                    .severity(CheckResult.AlertSeverity.NONE)
                    .build();
        }
        
        log.debug("Vérification du site: {}", site.getUrl());
        CheckResult result = siteChecker.check(site);
        result.setSite(site);
        result.setTimestamp(LocalDateTime.now());
        
        // Sauvegarde le résultat
        siteRepository.saveCheckResult(result);
        
        // Mise à jour du statut du site
        MonitoredSite.SiteStatus previousStatus = site.getCurrentStatus();
        MonitoredSite.SiteStatus newStatus;
        
        switch (result.getStatus()) {
            case SUCCESS:
                newStatus = MonitoredSite.SiteStatus.UP;
                break;
            case FAILURE:
                newStatus = MonitoredSite.SiteStatus.DOWN;
                break;
            case TIMEOUT:
                newStatus = MonitoredSite.SiteStatus.DEGRADED;
                break;
            default:
                newStatus = MonitoredSite.SiteStatus.UNKNOWN;
        }
        
        // Si le statut a changé, mise à jour et notification
        if (previousStatus != newStatus) {
            site.setCurrentStatus(newStatus);
            siteRepository.save(site);
            
            if (site.isNotifyOnIssue()) {
                if (newStatus == MonitoredSite.SiteStatus.UP && 
                    (previousStatus == MonitoredSite.SiteStatus.DOWN || previousStatus == MonitoredSite.SiteStatus.DEGRADED)) {
                    // Site récupéré, envoyer une notification de récupération
                    notificationService.sendRecoveryNotification(site, result);
                } else if (newStatus == MonitoredSite.SiteStatus.DOWN || newStatus == MonitoredSite.SiteStatus.DEGRADED) {
                    // Site dégradé, envoyer une alerte
                    notificationService.sendAlert(site, result);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Active/désactive le mode maintenance pour un site
     * @param url L'URL du site
     * @param maintenanceMode true pour activer le mode maintenance, false pour le désactiver
     * @return true si l'opération a réussi, false sinon
     */
    public boolean setMaintenanceMode(String url, boolean maintenanceMode) {
        Optional<MonitoredSite> siteOpt = siteRepository.findByUrl(url);
        if (siteOpt.isEmpty()) {
            return false;
        }
        
        MonitoredSite site = siteOpt.get();
        site.setMaintenanceMode(maintenanceMode);
        
        if (maintenanceMode) {
            site.setCurrentStatus(MonitoredSite.SiteStatus.MAINTENANCE);
        } else {
            site.setCurrentStatus(MonitoredSite.SiteStatus.UNKNOWN);
            // Déclencher une vérification immédiate
            CompletableFuture.runAsync(() -> checkSite(site), executorService);
        }
        
        siteRepository.save(site);
        return true;
    }
    
    /**
     * Récupère un site par son ID
     * @param siteId L'ID du site
     * @return Le site, ou empty si non trouvé
     */
    public Optional<MonitoredSite> getSiteById(Long siteId) {
        return siteRepository.findById(siteId);
    }
    
    /**
     * Génère un rapport pour une période donnée
     * @param from Date de début
     * @param to Date de fin
     * @param reportType Type de rapport (quotidien, hebdomadaire, etc.)
     * @return Le contenu HTML du rapport
     */
    public String generateReport(ZonedDateTime from, ZonedDateTime to, String reportType) {
        List<MonitoredSite> sites = getAllSites();
        if (sites.isEmpty()) {
            return "<p>Aucun site surveillé.</p>";
        }
        
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("<!DOCTYPE html><html><head><style>");
        reportBuilder.append("body { font-family: Arial, sans-serif; }");
        reportBuilder.append("h1, h2 { color: #333; }");
        reportBuilder.append("table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }");
        reportBuilder.append("th, td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }");
        reportBuilder.append("th { background-color: #f2f2f2; }");
        reportBuilder.append(".success { color: green; }");
        reportBuilder.append(".warning { color: orange; }");
        reportBuilder.append(".error { color: red; }");
        reportBuilder.append("</style></head><body>");
        
        reportBuilder.append("<h1>Rapport " + reportType + " WebGuardian</h1>");
        reportBuilder.append("<p>Période: " + from.toLocalDate() + " à " + to.toLocalDate() + "</p>");
        
        // Résumé global
        int totalSites = sites.size();
        int sitesUp = 0;
        int sitesDown = 0;
        int sitesDegraded = 0;
        
        for (MonitoredSite site : sites) {
            switch (site.getCurrentStatus()) {
                case UP:
                    sitesUp++;
                    break;
                case DOWN:
                    sitesDown++;
                    break;
                case DEGRADED:
                    sitesDegraded++;
                    break;
            }
        }
        
        reportBuilder.append("<h2>Résumé global</h2>");
        reportBuilder.append("<table>");
        reportBuilder.append("<tr><th>Total des sites</th><td>" + totalSites + "</td></tr>");
        reportBuilder.append("<tr><th>Sites opérationnels</th><td class='success'>" + sitesUp + "</td></tr>");
        reportBuilder.append("<tr><th>Sites dégradés</th><td class='warning'>" + sitesDegraded + "</td></tr>");
        reportBuilder.append("<tr><th>Sites en panne</th><td class='error'>" + sitesDown + "</td></tr>");
        reportBuilder.append("</table>");
        
        // Détail par site
        reportBuilder.append("<h2>Détail des sites</h2>");
        
        for (MonitoredSite site : sites) {
            reportBuilder.append("<h3>" + site.getName() + "</h3>");
            reportBuilder.append("<table>");
            reportBuilder.append("<tr><th>URL</th><td><a href='" + site.getUrl() + "'>" + site.getUrl() + "</a></td></tr>");
            
            String statusClass;
            switch (site.getCurrentStatus()) {
                case UP:
                    statusClass = "success";
                    break;
                case DOWN:
                    statusClass = "error";
                    break;
                case DEGRADED:
                    statusClass = "warning";
                    break;
                default:
                    statusClass = "";
            }
            
            reportBuilder.append("<tr><th>Statut actuel</th><td class='" + statusClass + "'>" + site.getCurrentStatus() + "</td></tr>");
            
            // Statistiques pour la période
            List<CheckResult> history = siteRepository.getCheckHistory(
                    site.getId(), 
                    from.toLocalDateTime(), 
                    to.toLocalDateTime());
            
            int totalChecks = history.size();
            if (totalChecks > 0) {
                long successChecks = history.stream()
                        .filter(r -> r.getStatus() == CheckResult.CheckStatus.SUCCESS)
                        .count();
                
                double uptime = (double) successChecks / totalChecks * 100.0;
                
                reportBuilder.append("<tr><th>Nombre de vérifications</th><td>" + totalChecks + "</td></tr>");
                reportBuilder.append("<tr><th>Disponibilité</th><td>" + String.format("%.2f%%", uptime) + "</td></tr>");
                
                // Temps de réponse moyen
                double avgResponseTime = history.stream()
                        .filter(r -> r.getResponseTime() != null)
                        .mapToLong(r -> r.getResponseTime().toMillis())
                        .average()
                        .orElse(0);
                
                reportBuilder.append("<tr><th>Temps de réponse moyen</th><td>" + String.format("%.2f ms", avgResponseTime) + "</td></tr>");
                
                // Incidents
                long incidents = history.stream()
                        .filter(r -> r.getStatus() == CheckResult.CheckStatus.FAILURE || r.getStatus() == CheckResult.CheckStatus.ERROR)
                        .count();
                
                reportBuilder.append("<tr><th>Incidents</th><td>" + incidents + "</td></tr>");
            } else {
                reportBuilder.append("<tr><th colspan='2'>Aucune donnée pour la période</th></tr>");
            }
            
            reportBuilder.append("</table>");
        }
        
        reportBuilder.append("</body></html>");
        return reportBuilder.toString();
    }
}
