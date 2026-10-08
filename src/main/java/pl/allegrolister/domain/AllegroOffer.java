package pl.allegrolister.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Oferta Allegro znana aplikacji (wystawiona przez nią albo zaimportowana).
 */
@Entity
@Table(name = "allegro_offer", uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "offer_id"}))
public class AllegroOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id")
    private AllegroAccount account;

    @Column(name = "offer_id", nullable = false)
    private String offerId;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(length = 200)
    private String name;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    private int stock;

    /** ACTIVE / INACTIVE / ACTIVATING / ENDED */
    private String status;

    private String categoryId;

    private String externalId;

    @Column(length = 1000)
    private String imageUrl;

    private Long templateId;

    private boolean createdByApp;

    /** Oferta zakończona przez synchronizację z powodu braku stanu - kandydat do wznowienia. */
    private boolean endedByApp;

    private Instant lastSyncAt;

    public String getOfferUrl() {
        return account != null && account.getEnvironment() != null ? account.getEnvironment().offerUrl(offerId) : null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AllegroAccount getAccount() { return account; }
    public void setAccount(AllegroAccount account) { this.account = account; }
    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public boolean isCreatedByApp() { return createdByApp; }
    public void setCreatedByApp(boolean createdByApp) { this.createdByApp = createdByApp; }
    public boolean isEndedByApp() { return endedByApp; }
    public void setEndedByApp(boolean endedByApp) { this.endedByApp = endedByApp; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
}
