package pl.allegrolister.allegro.model;

/**
 * Węzeł drzewa kategorii Allegro.
 */
public record CategoryNode(String id, String name, boolean leaf, String parentId) {
}
