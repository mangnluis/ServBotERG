package com.webguardian.infrastructure.web;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import com.webguardian.core.ports.SiteCheckerPort;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Implémentation du service de vérification de sites basée sur OkHttp
 */
@Slf4j
public class OkHttpSiteChecker implements SiteCheckerPort {
    private final OkHttpClient client;
    
    public OkHttpSiteChecker() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }
    
    @Override
    public CheckResult check(MonitoredSite site) {
        log.debug("Checking site: {}", site.getUrl());
        
        Request request = new Request.Builder()
                .url(site.getUrl())
                .header("User-Agent", "WebGuardian Monitoring Bot/1.0")
                .build();
        
        CheckResult.CheckResultBuilder resultBuilder = CheckResult.builder();
        
        try {
            Instant start = Instant.now();
            Response response = client.newCall(request).execute();
            Duration responseTime = Duration.between(start, Instant.now());
            
            int statusCode = response.code();
            long contentSize = 0;
            String responseBody = "";
            
            if (response.body() != null) {
                responseBody = response.body().string();
                contentSize = responseBody.length();
            }
            
            response.close();
            
            // Vérification du code de statut
            boolean isSuccess = statusCode >= 200 && statusCode < 400;
            
            // Vérification du contenu si nécessaire
            boolean contentCheckPassed = true;
            if (site.isCheckContent() && site.getContentCheckString() != null && !site.getContentCheckString().isEmpty()) {
                contentCheckPassed = responseBody.contains(site.getContentCheckString());
            }
            
            // Vérification du temps de réponse
            boolean isResponseTimeOk = true;
            if (site.getResponseTimeThreshold() != null) {
                isResponseTimeOk = responseTime.compareTo(site.getResponseTimeThreshold()) <= 0;
            }
            
            // Vérification SSL si nécessaire
            boolean sslCheckPassed = true;
            if (site.isSslCheck() && site.getUrl().startsWith("https://")) {
                sslCheckPassed = checkSSL(site);
            }
            
            // Détermination du statut global et de la sévérité
            CheckResult.CheckStatus status = isSuccess ? CheckResult.CheckStatus.SUCCESS : CheckResult.CheckStatus.FAILURE;
            
            CheckResult.AlertSeverity severity = CheckResult.AlertSeverity.NONE;
            if (!isSuccess) {
                severity = (statusCode >= 500) ? CheckResult.AlertSeverity.HIGH : CheckResult.AlertSeverity.MEDIUM;
            } else if (!contentCheckPassed) {
                status = CheckResult.CheckStatus.FAILURE;
                severity = CheckResult.AlertSeverity.MEDIUM;
            } else if (!isResponseTimeOk) {
                status = CheckResult.CheckStatus.FAILURE;
                severity = CheckResult.AlertSeverity.LOW;
            } else if (!sslCheckPassed) {
                status = CheckResult.CheckStatus.FAILURE;
                severity = CheckResult.AlertSeverity.HIGH;
            }
            
            return resultBuilder
                    .statusCode(statusCode)
                    .responseTime(responseTime)
                    .contentSize(contentSize)
                    .status(status)
                    .contentCheckPassed(contentCheckPassed)
                    .sslCheckPassed(sslCheckPassed)
                    .severity(severity)
                    .build();
            
        } catch (IOException e) {
            log.error("Error checking site {}: {}", site.getUrl(), e.getMessage(), e);
            
            return resultBuilder
                    .status(e instanceof java.net.SocketTimeoutException 
                            ? CheckResult.CheckStatus.TIMEOUT 
                            : CheckResult.CheckStatus.ERROR)
                    .errorMessage(e.getMessage())
                    .severity(CheckResult.AlertSeverity.HIGH)
                    .build();
        }
    }
    
    @Override
    public CompletableFuture<CheckResult> checkAsync(MonitoredSite site) {
        CompletableFuture<CheckResult> future = new CompletableFuture<>();
        
        Request request = new Request.Builder()
                .url(site.getUrl())
                .header("User-Agent", "WebGuardian Monitoring Bot/1.0")
                .build();
        
        Instant start = Instant.now();
        
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("Async check failed for {}: {}", site.getUrl(), e.getMessage(), e);
                
                future.complete(CheckResult.builder()
                        .status(e instanceof java.net.SocketTimeoutException 
                                ? CheckResult.CheckStatus.TIMEOUT 
                                : CheckResult.CheckStatus.ERROR)
                        .errorMessage(e.getMessage())
                        .severity(CheckResult.AlertSeverity.HIGH)
                        .build());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    Duration responseTime = Duration.between(start, Instant.now());
                    
                    int statusCode = response.code();
                    long contentSize = 0;
                    String responseBody = "";
                    
                    if (response.body() != null) {
                        responseBody = response.body().string();
                        contentSize = responseBody.length();
                    }
                    
                    // Même logique que la méthode synchrone
                    boolean isSuccess = statusCode >= 200 && statusCode < 400;
                    
                    boolean contentCheckPassed = true;
                    if (site.isCheckContent() && site.getContentCheckString() != null && !site.getContentCheckString().isEmpty()) {
                        contentCheckPassed = responseBody.contains(site.getContentCheckString());
                    }
                    
                    boolean isResponseTimeOk = true;
                    if (site.getResponseTimeThreshold() != null) {
                        isResponseTimeOk = responseTime.compareTo(site.getResponseTimeThreshold()) <= 0;
                    }
                    
                    boolean sslCheckPassed = true;
                    if (site.isSslCheck() && site.getUrl().startsWith("https://")) {
                        sslCheckPassed = checkSSL(site);
                    }
                    
                    CheckResult.CheckStatus status = isSuccess ? CheckResult.CheckStatus.SUCCESS : CheckResult.CheckStatus.FAILURE;
                    
                    CheckResult.AlertSeverity severity = CheckResult.AlertSeverity.NONE;
                    if (!isSuccess) {
                        severity = (statusCode >= 500) ? CheckResult.AlertSeverity.HIGH : CheckResult.AlertSeverity.MEDIUM;
                    } else if (!contentCheckPassed) {
                        status = CheckResult.CheckStatus.FAILURE;
                        severity = CheckResult.AlertSeverity.MEDIUM;
                    } else if (!isResponseTimeOk) {
                        status = CheckResult.CheckStatus.FAILURE;
                        severity = CheckResult.AlertSeverity.LOW;
                    } else if (!sslCheckPassed) {
                        status = CheckResult.CheckStatus.FAILURE;
                        severity = CheckResult.AlertSeverity.HIGH;
                    }
                    
                    future.complete(CheckResult.builder()
                            .statusCode(statusCode)
                            .responseTime(responseTime)
                            .contentSize(contentSize)
                            .status(status)
                            .contentCheckPassed(contentCheckPassed)
                            .sslCheckPassed(sslCheckPassed)
                            .severity(severity)
                            .build());
                    
                } finally {
                    response.close();
                }
            }
        });
        
        return future;
    }
    
    @Override
    public boolean checkContent(MonitoredSite site, String content) {
        try {
            Request request = new Request.Builder()
                    .url(site.getUrl())
                    .header("User-Agent", "WebGuardian Monitoring Bot/1.0")
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return false;
                }
                
                String responseBody = response.body().string();
                return responseBody.contains(content);
            }
        } catch (Exception e) {
            log.error("Error checking content for {}: {}", site.getUrl(), e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean checkSSL(MonitoredSite site) {
        if (!site.getUrl().startsWith("https://")) {
            return false;
        }
        
        try {
            Request request = new Request.Builder()
                    .url(site.getUrl())
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                // Si on arrive jusqu'ici sans exception, le certificat est probablement valide
                return true;
            }
        } catch (SSLPeerUnverifiedException e) {
            log.error("SSL certificate validation failed for {}: {}", site.getUrl(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error checking SSL for {}: {}", site.getUrl(), e.getMessage(), e);
            return false;
        }
    }
}
