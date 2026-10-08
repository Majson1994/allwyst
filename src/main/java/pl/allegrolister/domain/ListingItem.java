package pl.allegrolister.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Jedna pozycja formularza wystawiania (produkt -> przyszła oferta).
 */
@Entity
@Table(name = "listing_item")
public class ListingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_id")
    private ListingJob job;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(length = 200)
    private String title;

    private String categoryId;

    @Column(length = 1000)
    private String categoryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_mode")
    private ListingMode listingMode = ListingMode.AUTO;

    /** Ręcznie wskazany produkt z katalogu Allegro (UUID). */
    private String allegroProductId;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    private int quantity;

    private Long templateId;
    private String shippingRateId;
    private String returnPolicyId;
    private String impliedWarrantyId;
    private String warrantyId;
    private String responsibleProducerId;
    private String responsiblePersonId;

    /** Ręczne wartości parametrów: JSON {"paramId": ["wartość lub id słownika", ...]}. */
    @Column(length = 20000)
    private String parameterOverrides;

    @Enumerated(EnumType.STRING)
    private ListingStatus status = ListingStatus.DRAFT;

    private String offerId;

    @Column(length = 1000)
    private String operationUrl;

    @Column(length = 8000)
    private String message;

    @Column(length = 8000)
    private String warnings;

    @Column(length = 100000)
    private String lastRequest;

    private Instant submittedAt;
    private Instant finishedAt;

    public int getTitleLength() {
        return pl.allegrolister.service.TitleUtils.allegroLength(title);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ListingJob getJob() { return job; }
    public void setJob(ListingJob job) { this.job = job; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public ListingMode getListingMode() { return listingMode; }
    public void setListingMode(ListingMode listingMode) { this.listingMode = listingMode; }
    public String getAllegroProductId() { return allegroProductId; }
    public void setAllegroProductId(String allegroProductId) { this.allegroProductId = allegroProductId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getShippingRateId() { return shippingRateId; }
    public void setShippingRateId(String shippingRateId) { this.shippingRateId = shippingRateId; }
    public String getReturnPolicyId() { return returnPolicyId; }
    public void setReturnPolicyId(String returnPolicyId) { this.returnPolicyId = returnPolicyId; }
    public String getImpliedWarrantyId() { return impliedWarrantyId; }
    public void setImpliedWarrantyId(String impliedWarrantyId) { this.impliedWarrantyId = impliedWarrantyId; }
    public String getWarrantyId() { return warrantyId; }
    public void setWarrantyId(String warrantyId) { this.warrantyId = warrantyId; }
    public String getResponsibleProducerId() { return responsibleProducerId; }
    public void setResponsibleProducerId(String responsibleProducerId) { this.responsibleProducerId = responsibleProducerId; }
    public String getResponsiblePersonId() { return responsiblePersonId; }
    public void setResponsiblePersonId(String responsiblePersonId) { this.responsiblePersonId = responsiblePersonId; }
    public String getParameterOverrides() { return parameterOverrides; }
    public void setParameterOverrides(String parameterOverrides) { this.parameterOverrides = parameterOverrides; }
    public ListingStatus getStatus() { return status; }
    public void setStatus(ListingStatus status) { this.status = status; }
    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }
    public String getOperationUrl() { return operationUrl; }
    public void setOperationUrl(String operationUrl) { this.operationUrl = operationUrl; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getWarnings() { return warnings; }
    public void setWarnings(String warnings) { this.warnings = warnings; }
    public String getLastRequest() { return lastRequest; }
    public void setLastRequest(String lastRequest) { this.lastRequest = lastRequest; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
