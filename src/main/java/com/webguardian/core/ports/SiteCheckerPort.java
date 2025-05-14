package com.webguardian.core.ports;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;

import java.util.concurrent.CompletableFuture;

/**
 * Interface pour les services de vérification de sites web
 */
public interface SiteCheckerPort {
    /**
     * Effectue une vérification synchrone d'un site web
     * @param site Le site à vérifier
     * @return Le résultat de la vérification
     */
    CheckResult check(MonitoredSite site);
    
    /**
     * Effectue une vérification asynchrone d'un site web
     * @param site Le site à vérifier
     * @return CompletableFuture contenant le résultat de la vérification
     */
    CompletableFuture<CheckResult> checkAsync(MonitoredSite site);
    
    /**
     * Vérifie si un contenu spécifique est présent dans la page
     * @param site Le site à vérifier
     * @param content Le contenu à rechercher
     * @return true si le contenu est trouvé, false sinon
     */
    boolean checkContent(MonitoredSite site, String content);
    
    /**
     * Vérifie la validité du certificat SSL
     * @param site Le site à vérifier
     * @return true si le certificat est valide, false sinon
     */
    boolean checkSSL(MonitoredSite site);
}
