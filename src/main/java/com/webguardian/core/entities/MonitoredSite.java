package com.webguardian.core.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente un site web à surveiller
 */
@Entity
@Table(name = "monitored_sites")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoredSite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String url;
    
    // Paramètres de vérification
    private Duration checkInterval;
    private Duration responseTimeThreshold;
    private int maxRetries;
    
    @Enumerated(EnumType.STRING)
    private SiteStatus currentStatus;
    
    private String contentCheckString;
    private boolean checkContent;
    
    private boolean sslCheck;
    private boolean notifyOnIssue;
    
    @Builder.Default
    private boolean maintenanceMode = false;
    
    @Builder.Default
    @OneToMany(mappedBy = "site")
    private List<CheckResult> checkHistory = new ArrayList<>();
    
    public enum SiteStatus {
        UP,
        DOWN,
        DEGRADED,
        MAINTENANCE,
        UNKNOWN
    }
}
