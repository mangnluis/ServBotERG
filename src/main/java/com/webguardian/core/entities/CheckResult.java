package com.webguardian.core.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Représente le résultat d'une vérification d'un site web
 */
@Entity
@Table(name = "check_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "site_id")
    private MonitoredSite site;
    
    private LocalDateTime timestamp;
    
    private Integer statusCode;
    private Duration responseTime;
    private long contentSize;
    
    @Enumerated(EnumType.STRING)
    private CheckStatus status;
    
    private boolean contentCheckPassed;
    private boolean sslCheckPassed;
    
    private String errorMessage;
    
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;
    
    public enum CheckStatus {
        SUCCESS,
        FAILURE,
        TIMEOUT,
        ERROR
    }
    
    public enum AlertSeverity {
        NONE,
        LOW,
        MEDIUM, 
        HIGH,
        CRITICAL
    }
}
