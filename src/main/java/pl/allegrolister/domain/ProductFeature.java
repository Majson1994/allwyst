package pl.allegrolister.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Cecha produktu z magazynu (np. Kolor = czerwony). Źródło dla parametrów Allegro.
 */
@Embeddable
public class ProductFeature {

    @Column(name = "feature_name")
    private String name;

    @Column(name = "feature_value", length = 2000)
    private String value;

    public ProductFeature() {
    }

    public ProductFeature(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
