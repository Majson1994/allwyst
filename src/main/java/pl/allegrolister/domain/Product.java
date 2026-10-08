package pl.allegrolister.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Produkt w magazynie aplikacji (odpowiednik magazynu BaseLinkera).
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sku;

    private String ean;

    @Column(nullable = false, length = 500)
    private String name;

    private String manufacturer;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    private int quantity;

    private Integer vatRate;

    /** Waga w kg. */
    @Column(precision = 10, scale = 3)
    private BigDecimal weight;

    /** Kategoria w sklepie/magazynie - do powiązań z kategoriami Allegro. */
    private String shopCategory;

    @Column(length = 100000)
    private String description;

    /** Ręcznie wskazana kategoria Allegro (ma pierwszeństwo przed powiązaniem). */
    private String allegroCategoryId;

    /** Informacje o bezpieczeństwie produktu (GPSR) - tekst. */
    @Column(length = 4000)
    private String safetyInfo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_image", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "pos")
    @Column(name = "url", length = 2000)
    private List<String> images = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_feature", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "pos")
    private List<ProductFeature> features = new ArrayList<>();

    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** Wartość cechy po nazwie (bez rozróżniania wielkości liter). */
    public String feature(String featureName) {
        if (featureName == null) {
            return null;
        }
        for (ProductFeature f : features) {
            if (f.getName() != null && f.getName().trim().equalsIgnoreCase(featureName.trim())) {
                return f.getValue();
            }
        }
        return null;
    }

    public String getMainImage() {
        return images.isEmpty() ? null : images.get(0);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getEan() { return ean; }
    public void setEan(String ean) { this.ean = ean; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public Integer getVatRate() { return vatRate; }
    public void setVatRate(Integer vatRate) { this.vatRate = vatRate; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public String getShopCategory() { return shopCategory; }
    public void setShopCategory(String shopCategory) { this.shopCategory = shopCategory; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAllegroCategoryId() { return allegroCategoryId; }
    public void setAllegroCategoryId(String allegroCategoryId) { this.allegroCategoryId = allegroCategoryId; }
    public String getSafetyInfo() { return safetyInfo; }
    public void setSafetyInfo(String safetyInfo) { this.safetyInfo = safetyInfo; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public List<ProductFeature> getFeatures() { return features; }
    public void setFeatures(List<ProductFeature> features) { this.features = features; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
