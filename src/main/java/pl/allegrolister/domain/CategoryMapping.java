package pl.allegrolister.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Powiązanie kategorii ze sklepu/magazynu z kategorią Allegro (+ opcjonalny szablon).
 */
@Entity
@Table(name = "category_mapping")
public class CategoryMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String shopCategory;

    private String allegroCategoryId;

    @Column(length = 1000)
    private String allegroCategoryName;

    private Long templateId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopCategory() { return shopCategory; }
    public void setShopCategory(String shopCategory) { this.shopCategory = shopCategory; }
    public String getAllegroCategoryId() { return allegroCategoryId; }
    public void setAllegroCategoryId(String allegroCategoryId) { this.allegroCategoryId = allegroCategoryId; }
    public String getAllegroCategoryName() { return allegroCategoryName; }
    public void setAllegroCategoryName(String allegroCategoryName) { this.allegroCategoryName = allegroCategoryName; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
}
