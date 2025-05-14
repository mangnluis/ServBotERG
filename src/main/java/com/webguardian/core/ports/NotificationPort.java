package com.webguardian.core.ports;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;

/**
 * Interface pour les services de notification
 */
public interface NotificationPort {
    /**
     * Envoie une alerte concernant un site
     * @param site Le site concerné
     * @param checkResult Le résultat de la vérification
     * @return true si l'alerte a été envoyée avec succès, false sinon
     */
    boolean sendAlert(MonitoredSite site, CheckResult checkResult);
    
    /**
     * Envoie une notification de récupération (site de nouveau disponible)
     * @param site Le site concerné
     * @param checkResult Le résultat de la vérification
     * @return true si la notification a été envoyée avec succès, false sinon
     */
    boolean sendRecoveryNotification(MonitoredSite site, CheckResult checkResult);
    
    /**
     * Envoie un rapport périodique
     * @param reportContent Le contenu du rapport
     * @param reportType Le type de rapport (quotidien, hebdomadaire, etc.)
     * @return true si le rapport a été envoyé avec succès, false sinon
     */
    boolean sendReport(String reportContent, String reportType);
}
