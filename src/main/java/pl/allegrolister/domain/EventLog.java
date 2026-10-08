package pl.allegrolister.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dziennik zdarzeń (wystawianie, synchronizacja, operacje na ofertach).
 */
@Entity
@Table(name = "event_log")
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    /** INFO / WARN / ERROR */
    @Column(name = "log_level")
    private String level;

    /** AUTH / LISTING / OFFERS / SYNC / IMPORT */
    private String area;

    private Long accountId;

    @Column(length = 2000)
    private String message;

    @Column(length = 20000)
    private String details;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
