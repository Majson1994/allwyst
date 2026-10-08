package pl.allegrolister.allegro.model;

/**
 * Produkt z Katalogu Produktów Allegro (GET /sale/products).
 */
public record CatalogProduct(String id, String name, String categoryId, String publicationStatus, String imageUrl) {

    public boolean isListed() {
        return "LISTED".equalsIgnoreCase(publicationStatus);
    }
}
