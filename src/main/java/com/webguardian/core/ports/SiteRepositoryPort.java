package com.webguardian.core.ports;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Interface pour la persistance des données
 */
public interface SiteRepositoryPort {
    /**
     * Sauvegarde un site à surveiller
     * @param site Le site à sauvegarder
     * @return Le site sauvegardé avec son ID
     */
    MonitoredSite save(MonitoredSite site);
    
    /**
     * Supprime un site
     * @param siteId L'ID du site à supprimer
     */
    void delete(Long siteId);
    
    /**
     * Récupère un site par son ID
     * @param siteId L'ID du site à récupérer
     * @return Le site, ou empty si non trouvé
     */
    Optional<MonitoredSite> findById(Long siteId);
    
    /**
     * Récupère un site par son URL
     * @param url L'URL du site à récupérer
     * @return Le site, ou empty si non trouvé
     */
    Optional<MonitoredSite> findByUrl(String url);
    
    /**
     * Récupère tous les sites à surveiller
     * @return La liste des sites
     */
    List<MonitoredSite> findAll();
    
    /**
     * Sauvegarde un résultat de vérification
     * @param checkResult Le résultat à sauvegarder
     * @return Le résultat sauvegardé avec son ID
     */
    CheckResult saveCheckResult(CheckResult checkResult);
    
    /**
     * Récupère l'historique des vérifications pour un site
     * @param siteId L'ID du site
     * @param from Date de début
     * @param to Date de fin
     * @return La liste des résultats de vérification pour la période
     */
    List<CheckResult> getCheckHistory(Long siteId, LocalDateTime from, LocalDateTime to);
}
