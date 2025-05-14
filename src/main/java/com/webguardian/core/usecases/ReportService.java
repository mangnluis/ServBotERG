package com.webguardian.core.usecases;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.ports.NotificationPort;
import com.webguardian.core.ports.SiteRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service pour la génération de rapports
 */
@Slf4j
@RequiredArgsConstructor
public class ReportService {
    private final MonitoringService monitoringService;
    private final SiteRepositoryPort siteRepository;
    private final NotificationPort notificationService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    /**
     * Génère et envoie un rapport quotidien
     * @return true si le rapport a été généré et envoyé avec succès
     */
    public boolean generateAndSendDailyReport() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime startOfDay = now.toLocalDate().atStartOfDay(now.getZone());
        ZonedDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
        
        if (startOfDay.isAfter(now)) {
            startOfDay = startOfDay.minusDays(1);
            endOfDay = endOfDay.minusDays(1);
        }
        
        String reportType = "Quotidien";
        String reportContent = monitoringService.generateReport(startOfDay, endOfDay, reportType);
        
        log.info("Génération du rapport quotidien pour le {}", now.format(DATE_FORMATTER));
        return notificationService.sendReport(reportContent, reportType);
    }
    
    /**
     * Génère et envoie un rapport hebdomadaire
     * @return true si le rapport a été généré et envoyé avec succès
     */
    public boolean generateAndSendWeeklyReport() {
        ZonedDateTime now = ZonedDateTime.now();
        DayOfWeek firstDayOfWeek = DayOfWeek.MONDAY;
        
        // Trouver le lundi de cette semaine
        ZonedDateTime startOfWeek = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDayOfWeek))
                .toLocalDate().atStartOfDay(now.getZone());
        ZonedDateTime endOfWeek = startOfWeek.plusWeeks(1).minusNanos(1);
        
        String reportType = "Hebdomadaire";
        String reportContent = monitoringService.generateReport(startOfWeek, endOfWeek, reportType);
        
        log.info("Génération du rapport hebdomadaire du {} au {}", 
                startOfWeek.format(DATE_FORMATTER), endOfWeek.format(DATE_FORMATTER));
        return notificationService.sendReport(reportContent, reportType);
    }
    
    /**
     * Génère et envoie un rapport mensuel
     * @return true si le rapport a été généré et envoyé avec succès
     */
    public boolean generateAndSendMonthlyReport() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay(now.getZone());
        ZonedDateTime endOfMonth = startOfMonth.plusMonths(1).minusNanos(1);
        
        String reportType = "Mensuel";
        String reportContent = monitoringService.generateReport(startOfMonth, endOfMonth, reportType);
        
        log.info("Génération du rapport mensuel pour {}", startOfMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        return notificationService.sendReport(reportContent, reportType);
    }
    
    /**
     * Génère un rapport personnalisé pour une période spécifique
     * @param fromDate Date de début
     * @param toDate Date de fin
     * @return le contenu HTML du rapport
     */
    public String generateCustomReport(LocalDate fromDate, LocalDate toDate) {
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime from = fromDate.atStartOfDay(zoneId);
        ZonedDateTime to = toDate.atTime(LocalTime.MAX).atZone(zoneId);
        
        String reportType = "Personnalisé";
        String reportContent = monitoringService.generateReport(from, to, reportType);
        
        log.info("Génération du rapport personnalisé du {} au {}", fromDate, toDate);
        return reportContent;
    }
    
    /**
     * Génère un rapport de performances pour un site spécifique
     * @param siteId ID du site
     * @param days Nombre de jours en arrière pour le rapport
     * @return le contenu HTML du rapport
     */
    public String generateSitePerformanceReport(Long siteId, int days) {
        if (days <= 0) {
            days = 7; // Par défaut, 7 jours
        }
        
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime startDate = now.minusDays(days).toLocalDate().atStartOfDay(now.getZone());
        
        return siteRepository.findById(siteId).map(site -> {
            List<CheckResult> history = siteRepository.getCheckHistory(
                    siteId,
                    startDate.toLocalDateTime(),
                    now.toLocalDateTime());
            
            StringBuilder report = new StringBuilder();
            report.append("<!DOCTYPE html><html><head><style>");
            report.append("body { font-family: Arial, sans-serif; }");
            report.append("h1, h2 { color: #333; }");
            report.append("table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }");
            report.append("th, td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }");
            report.append("th { background-color: #f2f2f2; }");
            report.append(".success { color: green; }");
            report.append(".warning { color: orange; }");
            report.append(".error { color: red; }");
            report.append("</style></head><body>");
            
            report.append("<h1>Rapport de performances pour ").append(site.getName()).append("</h1>");
            report.append("<p>Période: derniers ").append(days).append(" jours</p>");
            
            report.append("<h2>Informations générales</h2>");
            report.append("<table>");
            report.append("<tr><th>URL</th><td><a href='").append(site.getUrl()).append("'>").append(site.getUrl()).append("</a></td></tr>");
            report.append("<tr><th>Fréquence de vérification</th><td>").append(formatDuration(site.getCheckInterval())).append("</td></tr>");
            
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
            report.append("<tr><th>Statut actuel</th><td class='").append(statusClass).append("'>").append(site.getCurrentStatus()).append("</td></tr>");
            
            if (!history.isEmpty()) {
                // Calcul des statistiques
                int totalChecks = history.size();
                long successChecks = history.stream()
                        .filter(r -> r.getStatus() == CheckResult.CheckStatus.SUCCESS)
                        .count();
                
                double uptime = (double) successChecks / totalChecks * 100.0;
                
                // Temps de réponse moyen
                double avgResponseTime = history.stream()
                        .filter(r -> r.getResponseTime() != null)
                        .mapToLong(r -> r.getResponseTime().toMillis())
                        .average()
                        .orElse(0);
                
                // Incidents par jour
                Map<LocalDate, Long> incidentsByDay = history.stream()
                        .filter(r -> r.getStatus() == CheckResult.CheckStatus.FAILURE || r.getStatus() == CheckResult.CheckStatus.ERROR)
                        .collect(Collectors.groupingBy(
                                r -> r.getTimestamp().toLocalDate(),
                                Collectors.counting()));
                
                report.append("<tr><th>Nombre de vérifications</th><td>").append(totalChecks).append("</td></tr>");
                report.append("<tr><th>Disponibilité</th><td>").append(String.format("%.2f%%", uptime)).append("</td></tr>");
                report.append("<tr><th>Temps de réponse moyen</th><td>").append(String.format("%.2f ms", avgResponseTime)).append("</td></tr>");
                
                // Affichage des incidents
                report.append("</table>");
                
                report.append("<h2>Incidents sur la période</h2>");
                
                if (incidentsByDay.isEmpty()) {
                    report.append("<p>Aucun incident sur la période.</p>");
                } else {
                    report.append("<table>");
                    report.append("<tr><th>Date</th><th>Nombre d'incidents</th></tr>");
                    
                    incidentsByDay.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(entry -> {
                                report.append("<tr><td>").append(entry.getKey().format(DATE_FORMATTER))
                                        .append("</td><td>").append(entry.getValue()).append("</td></tr>");
                            });
                    
                    report.append("</table>");
                }
                
                // Temps de réponse journalier
                report.append("<h2>Temps de réponse moyen par jour</h2>");
                report.append("<table>");
                report.append("<tr><th>Date</th><th>Temps de réponse moyen</th></tr>");
                
                Map<LocalDate, Double> avgResponseByDay = history.stream()
                        .filter(r -> r.getResponseTime() != null)
                        .collect(Collectors.groupingBy(
                                r -> r.getTimestamp().toLocalDate(),
                                Collectors.averagingDouble(r -> r.getResponseTime().toMillis())));
                
                avgResponseByDay.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            report.append("<tr><td>").append(entry.getKey().format(DATE_FORMATTER))
                                    .append("</td><td>").append(String.format("%.2f ms", entry.getValue())).append("</td></tr>");
                        });
                
                report.append("</table>");
                
            } else {
                report.append("<tr><th colspan='2'>Aucune donnée pour la période</th></tr>");
                report.append("</table>");
            }
            
            report.append("</body></html>");
            return report.toString();
            
        }).orElse("<p>Site non trouvé.</p>");
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
}
