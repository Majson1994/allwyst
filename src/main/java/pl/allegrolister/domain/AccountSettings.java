package pl.allegrolister.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Domyślne ustawienia ofert dla konta (odpowiednik "Ustawienia aukcji" w BaseLinkerze)
 * oraz ustawienia modułu synchronizacji stanów.
 */
@Embeddable
public class AccountSettings {

    // --- domyślne wartości formularza wystawiania ---
    private String defaultCategoryId;
    private String defaultCategoryName;
    private Long defaultTemplateId;
    private String shippingRateId;
    private String returnPolicyId;
    private String impliedWarrantyId;
    private String warrantyId;
    private String responsibleProducerId;
    private String responsiblePersonId;
    private String handlingTime = "PT24H";
    private String invoiceType = "VAT";

    @Column(precision = 12, scale = 4)
    private BigDecimal priceMultiplier = BigDecimal.ONE;

    @Column(precision = 12, scale = 2)
    private BigDecimal priceAddition = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private ListingMode defaultListingMode = ListingMode.AUTO;

    private boolean publishImmediately = true;
    private boolean autoMatchCategory = true;

    @Enumerated(EnumType.STRING)
    private SafetyMode safetyMode = SafetyMode.OMIT;

    @Column(length = 4000)
    private String defaultSafetyText;

    // --- synchronizacja stanów i cen (magazyn -> Allegro) ---
    private boolean syncStock = false;
    private boolean syncPrice = false;
    private boolean endWhenZero = true;
    private boolean renewWhenAvailable = false;

    public String getDefaultCategoryId() { return defaultCategoryId; }
    public void setDefaultCategoryId(String defaultCategoryId) { this.defaultCategoryId = defaultCategoryId; }
    public String getDefaultCategoryName() { return defaultCategoryName; }
    public void setDefaultCategoryName(String defaultCategoryName) { this.defaultCategoryName = defaultCategoryName; }
    public Long getDefaultTemplateId() { return defaultTemplateId; }
    public void setDefaultTemplateId(Long defaultTemplateId) { this.defaultTemplateId = defaultTemplateId; }
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
    public String getHandlingTime() { return handlingTime; }
    public void setHandlingTime(String handlingTime) { this.handlingTime = handlingTime; }
    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
    public BigDecimal getPriceMultiplier() { return priceMultiplier; }
    public void setPriceMultiplier(BigDecimal priceMultiplier) { this.priceMultiplier = priceMultiplier; }
    public BigDecimal getPriceAddition() { return priceAddition; }
    public void setPriceAddition(BigDecimal priceAddition) { this.priceAddition = priceAddition; }
    public ListingMode getDefaultListingMode() { return defaultListingMode; }
    public void setDefaultListingMode(ListingMode defaultListingMode) { this.defaultListingMode = defaultListingMode; }
    public boolean isPublishImmediately() { return publishImmediately; }
    public void setPublishImmediately(boolean publishImmediately) { this.publishImmediately = publishImmediately; }
    public boolean isAutoMatchCategory() { return autoMatchCategory; }
    public void setAutoMatchCategory(boolean autoMatchCategory) { this.autoMatchCategory = autoMatchCategory; }
    public SafetyMode getSafetyMode() { return safetyMode; }
    public void setSafetyMode(SafetyMode safetyMode) { this.safetyMode = safetyMode; }
    public String getDefaultSafetyText() { return defaultSafetyText; }
    public void setDefaultSafetyText(String defaultSafetyText) { this.defaultSafetyText = defaultSafetyText; }
    public boolean isSyncStock() { return syncStock; }
    public void setSyncStock(boolean syncStock) { this.syncStock = syncStock; }
    public boolean isSyncPrice() { return syncPrice; }
    public void setSyncPrice(boolean syncPrice) { this.syncPrice = syncPrice; }
    public boolean isEndWhenZero() { return endWhenZero; }
    public void setEndWhenZero(boolean endWhenZero) { this.endWhenZero = endWhenZero; }
    public boolean isRenewWhenAvailable() { return renewWhenAvailable; }
    public void setRenewWhenAvailable(boolean renewWhenAvailable) { this.renewWhenAvailable = renewWhenAvailable; }

    public boolean isAnySyncEnabled() {
        return syncStock || syncPrice || renewWhenAvailable;
    }
}
