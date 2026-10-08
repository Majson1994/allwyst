package pl.allegrolister.web.form;

import pl.allegrolister.domain.ListingMode;

/**
 * Pola jednej pozycji formularza wystawiania.
 */
public class ItemForm {

    private Long id;
    private boolean selected;
    private String title;
    private String categoryId;
    private String categoryName;
    private ListingMode listingMode;
    private String allegroProductId;
    private String price;
    private String quantity;
    private Long templateId;
    private String shippingRateId;
    private String returnPolicyId;
    private String impliedWarrantyId;
    private String warrantyId;
    private String responsibleProducerId;
    private String responsiblePersonId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
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
    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
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
}
