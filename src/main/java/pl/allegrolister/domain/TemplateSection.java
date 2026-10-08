package pl.allegrolister.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Sekcja szablonu opisu. Tekst może zawierać tagi ([nazwa], [opis], [cechy]...),
 * pola zdjęć zawierają tag [zdjecie_N] albo bezpośredni URL.
 */
@Embeddable
public class TemplateSection {

    @Enumerated(EnumType.STRING)
    @Column(name = "layout")
    private SectionLayout layout = SectionLayout.TEXT;

    @Column(name = "section_text", length = 20000)
    private String text;

    @Column(name = "image1", length = 1000)
    private String image1;

    @Column(name = "image2", length = 1000)
    private String image2;

    public TemplateSection() {
    }

    public TemplateSection(SectionLayout layout, String text, String image1, String image2) {
        this.layout = layout;
        this.text = text;
        this.image1 = image1;
        this.image2 = image2;
    }

    public SectionLayout getLayout() { return layout; }
    public void setLayout(SectionLayout layout) { this.layout = layout; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getImage1() { return image1; }
    public void setImage1(String image1) { this.image1 = image1; }
    public String getImage2() { return image2; }
    public void setImage2(String image2) { this.image2 = image2; }
}
