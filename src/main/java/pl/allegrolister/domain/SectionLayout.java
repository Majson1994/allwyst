package pl.allegrolister.domain;

/**
 * Układ sekcji opisu Allegro (sekcja ma maks. 2 elementy).
 */
public enum SectionLayout {
    TEXT("Tekst", false, true, false),
    IMAGE("Zdjęcie", true, false, false),
    IMAGE_TEXT("Zdjęcie + tekst", true, true, false),
    TEXT_IMAGE("Tekst + zdjęcie", true, true, false),
    IMAGE_IMAGE("Dwa zdjęcia", true, false, true);

    private final String label;
    private final boolean hasImage;
    private final boolean hasText;
    private final boolean twoImages;

    SectionLayout(String label, boolean hasImage, boolean hasText, boolean twoImages) {
        this.label = label;
        this.hasImage = hasImage;
        this.hasText = hasText;
        this.twoImages = twoImages;
    }

    public String getLabel() { return label; }
    public boolean isHasImage() { return hasImage; }
    public boolean isHasText() { return hasText; }
    public boolean isTwoImages() { return twoImages; }
}
