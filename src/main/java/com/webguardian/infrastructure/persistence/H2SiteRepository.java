package com.webguardian.infrastructure.persistence;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.ports.SiteRepositoryPort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.query.Query;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Implémentation du repository utilisant H2 et Hibernate
 */
@Slf4j
public class H2SiteRepository implements SiteRepositoryPort {
    private SessionFactory sessionFactory;
    private HikariDataSource dataSource;
    
    /**
     * Constructeur avec configuration de la base de données
     */
    public H2SiteRepository(String jdbcUrl, String username, String password, int poolSize) {
        initDataSource(jdbcUrl, username, password, poolSize);
        initHibernate();
    }
    
    /**
     * Initialise le pool de connexions Hikari
     */
    private void initDataSource(String jdbcUrl, String username, String password, int poolSize) {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(poolSize);
            config.setAutoCommit(false);
            
            // Configuration spécifique pour H2
            if (jdbcUrl.contains("h2")) {
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                // Auto-création des tables
                if (!jdbcUrl.contains("AUTO_SERVER=TRUE")) {
                    if (jdbcUrl.contains("?")) {
                        config.setJdbcUrl(jdbcUrl + ";AUTO_SERVER=TRUE");
                    } else {
                        config.setJdbcUrl(jdbcUrl + "?AUTO_SERVER=TRUE");
                    }
                }
            }
            
            dataSource = new HikariDataSource(config);
            log.info("Pool de connexions HikariCP initialisé avec succès");
            
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation du pool de connexions: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible d'initialiser le pool de connexions", e);
        }
    }
    
    /**
     * Initialise Hibernate
     */
    private void initHibernate() {
        try {
            Properties hibernateProperties = new Properties();
            hibernateProperties.put("hibernate.connection.datasource", dataSource);
            hibernateProperties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            hibernateProperties.put("hibernate.hbm2ddl.auto", "update");
            hibernateProperties.put("hibernate.show_sql", "false");
            hibernateProperties.put("hibernate.format_sql", "true");
            hibernateProperties.put("hibernate.use_sql_comments", "true");
            
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySettings(hibernateProperties)
                    .build();
            
            sessionFactory = new MetadataSources(registry)
                    .addAnnotatedClass(MonitoredSite.class)
                    .addAnnotatedClass(CheckResult.class)
                    .buildMetadata()
                    .buildSessionFactory();
            
            log.info("SessionFactory Hibernate initialisée avec succès");
            
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation d'Hibernate: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible d'initialiser Hibernate", e);
        }
    }
    
    @Override
    public MonitoredSite save(MonitoredSite site) {
        Session session = sessionFactory.openSession();
        Transaction tx = null;
        
        try {
            tx = session.beginTransaction();
            
            if (site.getId() == null) {
                // Nouveau site
                session.persist(site);
                log.debug("Nouveau site créé: {}", site.getUrl());
            } else {
                // Mise à jour d'un site existant
                site = (MonitoredSite) session.merge(site);
                log.debug("Site mis à jour: {}", site.getUrl());
            }
            
            tx.commit();
            return site;
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            log.error("Erreur lors de la sauvegarde du site: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de sauvegarder le site", e);
        } finally {
            session.close();
        }
    }
    
    @Override
    public void delete(Long siteId) {
        Session session = sessionFactory.openSession();
        Transaction tx = null;
        
        try {
            tx = session.beginTransaction();
            
            MonitoredSite site = session.get(MonitoredSite.class, siteId);
            if (site != null) {
                // Suppression des résultats de vérification associés
                Query<?> query = session.createQuery("DELETE FROM CheckResult cr WHERE cr.site.id = :siteId");
                query.setParameter("siteId", siteId);
                query.executeUpdate();
                
                session.remove(site);
                log.debug("Site supprimé: {}", site.getUrl());
            }
            
            tx.commit();
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            log.error("Erreur lors de la suppression du site: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de supprimer le site", e);
        } finally {
            session.close();
        }
    }
    
    @Override
    public Optional<MonitoredSite> findById(Long siteId) {
        Session session = sessionFactory.openSession();
        
        try {
            MonitoredSite site = session.get(MonitoredSite.class, siteId);
            return Optional.ofNullable(site);
        } catch (Exception e) {
            log.error("Erreur lors de la recherche du site par ID: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de rechercher le site par ID", e);
        } finally {
            session.close();
        }
    }
    
    @Override
    public Optional<MonitoredSite> findByUrl(String url) {
        Session session = sessionFactory.openSession();
        
        try {
            Query<MonitoredSite> query = session.createQuery(
                    "FROM MonitoredSite ms WHERE ms.url = :url", MonitoredSite.class);
            query.setParameter("url", url);
            return Optional.ofNullable(query.uniqueResult());
        } catch (Exception e) {
            log.error("Erreur lors de la recherche du site par URL: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de rechercher le site par URL", e);
        } finally {
            session.close();
        }
    }
    
    @Override
    public List<MonitoredSite> findAll() {
        Session session = sessionFactory.openSession();
        
        try {
            Query<MonitoredSite> query = session.createQuery(
                    "FROM MonitoredSite", MonitoredSite.class);
            return query.getResultList();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de tous les sites: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de récupérer tous les sites", e);
        } finally {
            session.close();
        }
    }
    
    @Override
    public CheckResult saveCheckResult(CheckResult checkResult) {
        Session session = sessionFactory.openSession();
        Transaction tx = null;
        
        try {
            tx = session.beginTransaction();
            
            session.persist(checkResult);
            
            tx.commit();
            return checkResult;
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            log.error("Erreur lors de la sauvegarde du résultat de vérification: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de sauvegarder le résultat de vérification", e);
        } finally {
            session.close();
        }
    }
    
    @Override
    public List<CheckResult> getCheckHistory(Long siteId, LocalDateTime from, LocalDateTime to) {
        Session session = sessionFactory.openSession();
        
        try {
            Query<CheckResult> query = session.createQuery(
                    "FROM CheckResult cr WHERE cr.site.id = :siteId " +
                            "AND cr.timestamp BETWEEN :fromDate AND :toDate " +
                            "ORDER BY cr.timestamp DESC", CheckResult.class);
            query.setParameter("siteId", siteId);
            query.setParameter("fromDate", from);
            query.setParameter("toDate", to);
            
            return query.getResultList();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de l'historique des vérifications: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de récupérer l'historique des vérifications", e);
        } finally {
            session.close();
        }
    }
    
    /**
     * Ferme les ressources du repository
     */
    public void close() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
