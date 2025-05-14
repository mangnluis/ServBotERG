package com.webguardian.infrastructure.persistence;

import com.webguardian.core.entities.CheckResult;
import com.webguardian.core.entities.MonitoredSite;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class H2SiteRepositoryTest {

    @Mock
    private SessionFactory sessionFactory;
    
    @Mock
    private Session session;
    
    @Mock
    private Transaction transaction;
    
    @Mock
    private Query<MonitoredSite> siteQuery;
    
    @Mock
    private Query<CheckResult> resultQuery;
    
    private H2SiteRepository repository;
    
    @BeforeEach
    public void setup() {
        repository = new H2SiteRepository(sessionFactory);
        
        // Common setup
        when(sessionFactory.openSession()).thenReturn(session);
        when(session.beginTransaction()).thenReturn(transaction);
    }
    
    @Test
    public void testSave() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder()
                .name("Test Site")
                .url("https://example.com")
                .build();
        
        // Act
        MonitoredSite result = repository.save(site);
        
        // Assert
        assertNotNull(result);
        assertEquals(site, result);
        
        verify(session).saveOrUpdate(site);
        verify(transaction).commit();
        verify(session).close();
    }
    
    @Test
    public void testFindById() {
        // Arrange
        Long id = 1L;
        MonitoredSite site = MonitoredSite.builder()
                .id(id)
                .name("Test Site")
                .url("https://example.com")
                .build();
        
        when(session.get(MonitoredSite.class, id)).thenReturn(site);
        
        // Act
        Optional<MonitoredSite> result = repository.findById(id);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(site, result.get());
        
        verify(session).get(MonitoredSite.class, id);
        verify(session).close();
    }
    
    @Test
    public void testFindByIdNotFound() {
        // Arrange
        Long id = 1L;
        when(session.get(MonitoredSite.class, id)).thenReturn(null);
        
        // Act
        Optional<MonitoredSite> result = repository.findById(id);
        
        // Assert
        assertFalse(result.isPresent());
        
        verify(session).get(MonitoredSite.class, id);
        verify(session).close();
    }
    
    @Test
    public void testFindByUrl() {
        // Arrange
        String url = "https://example.com";
        MonitoredSite site = MonitoredSite.builder()
                .id(1L)
                .name("Test Site")
                .url(url)
                .build();
        
        when(session.createQuery(anyString(), eq(MonitoredSite.class))).thenReturn(siteQuery);
        when(siteQuery.setParameter(anyString(), eq(url))).thenReturn(siteQuery);
        when(siteQuery.uniqueResultOptional()).thenReturn(Optional.of(site));
        
        // Act
        Optional<MonitoredSite> result = repository.findByUrl(url);
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(site, result.get());
        assertEquals(url, result.get().getUrl());
        
        verify(session).createQuery(anyString(), eq(MonitoredSite.class));
        verify(siteQuery).setParameter(anyString(), eq(url));
        verify(siteQuery).uniqueResultOptional();
        verify(session).close();
    }
    
    @Test
    public void testFindAll() {
        // Arrange
        List<MonitoredSite> sites = Arrays.asList(
            MonitoredSite.builder().id(1L).url("https://site1.com").build(),
            MonitoredSite.builder().id(2L).url("https://site2.com").build()
        );
        
        when(session.createQuery(anyString(), eq(MonitoredSite.class))).thenReturn(siteQuery);
        when(siteQuery.list()).thenReturn(sites);
        
        // Act
        List<MonitoredSite> result = repository.findAll();
        
        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(sites, result);
        
        verify(session).createQuery(anyString(), eq(MonitoredSite.class));
        verify(siteQuery).list();
        verify(session).close();
    }
    
    @Test
    public void testDelete() {
        // Arrange
        Long id = 1L;
        MonitoredSite site = MonitoredSite.builder().id(id).build();
        when(session.get(MonitoredSite.class, id)).thenReturn(site);
        
        // Act
        boolean result = repository.delete(id);
        
        // Assert
        assertTrue(result);
        
        verify(session).get(MonitoredSite.class, id);
        verify(session).delete(site);
        verify(transaction).commit();
        verify(session).close();
    }
    
    @Test
    public void testDeleteNotFound() {
        // Arrange
        Long id = 1L;
        when(session.get(MonitoredSite.class, id)).thenReturn(null);
        
        // Act
        boolean result = repository.delete(id);
        
        // Assert
        assertFalse(result);
        
        verify(session).get(MonitoredSite.class, id);
        verify(session, never()).delete(any());
        verify(transaction).rollback();
        verify(session).close();
    }
    
    @Test
    public void testSaveCheckResult() {
        // Arrange
        CheckResult checkResult = CheckResult.builder()
                .id(1L)
                .site(MonitoredSite.builder().id(1L).build())
                .timestamp(LocalDateTime.now())
                .statusCode(200)
                .status(CheckResult.CheckStatus.SUCCESS)
                .build();
        
        // Act
        CheckResult result = repository.saveCheckResult(checkResult);
        
        // Assert
        assertNotNull(result);
        assertEquals(checkResult, result);
        
        verify(session).saveOrUpdate(checkResult);
        verify(transaction).commit();
        verify(session).close();
    }
    
    @Test
    public void testGetCheckHistory() {
        // Arrange
        Long siteId = 1L;
        ZonedDateTime startDate = ZonedDateTime.now().minusDays(7);
        ZonedDateTime endDate = ZonedDateTime.now();
        
        List<CheckResult> history = Arrays.asList(
            CheckResult.builder().id(1L).status(CheckResult.CheckStatus.SUCCESS).build(),
            CheckResult.builder().id(2L).status(CheckResult.CheckStatus.FAILURE).build()
        );
        
        when(session.createQuery(anyString(), eq(CheckResult.class))).thenReturn(resultQuery);
        when(resultQuery.setParameter(anyString(), any())).thenReturn(resultQuery);
        when(resultQuery.list()).thenReturn(history);
        
        // Act
        List<CheckResult> results = repository.getCheckHistory(siteId, startDate, endDate);
        
        // Assert
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(history, results);
        
        verify(session).createQuery(anyString(), eq(CheckResult.class));
        verify(resultQuery, times(3)).setParameter(anyString(), any());
        verify(resultQuery).list();
        verify(session).close();
    }
    
    @Test
    public void testHandleException() {
        // Arrange
        MonitoredSite site = MonitoredSite.builder().name("Test Site").build();
        doThrow(new RuntimeException("Database error")).when(session).saveOrUpdate(site);
        
        // Act & Assert
        assertThrows(RuntimeException.class, () -> repository.save(site));
        
        verify(session).saveOrUpdate(site);
        verify(transaction).rollback();
        verify(session).close();
    }
}