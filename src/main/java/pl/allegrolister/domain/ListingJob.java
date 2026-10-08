package pl.allegrolister.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Formularz wystawiania - partia produktów wystawianych na jedno konto.
 */
@Entity
@Table(name = "listing_job")
public class ListingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id")
    private AllegroAccount account;

    private String name;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ListingItem> items = new ArrayList<>();

    public long countByStatus(ListingStatus status) {
        return items.stream().filter(i -> i.getStatus() == status).count();
    }

    /** Wersja dla widoków: count('ACTIVE'). */
    public long count(String status) {
        return countByStatus(ListingStatus.valueOf(status));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AllegroAccount getAccount() { return account; }
    public void setAccount(AllegroAccount account) { this.account = account; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<ListingItem> getItems() { return items; }
    public void setItems(List<ListingItem> items) { this.items = items; }
}
