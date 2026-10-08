package pl.allegrolister.domain;

/**
 * Operacje grupowe w Zarządzaniu ofertami (odpowiednik "Operacje" w BaseLinkerze).
 */
public enum OfferOperation {
    SYNC_STOCK("Ustaw ilość z magazynu", false),
    SYNC_PRICE("Ustaw cenę z magazynu (z mnożnikiem konta)", false),
    SET_PRICE("Ustaw cenę na wartość", true),
    CHANGE_PRICE_PERCENT("Zmień cenę o % (np. 10 albo -5)", true),
    SET_QUANTITY("Ustaw ilość na wartość", true),
    END("Zakończ oferty", false),
    ACTIVATE("Aktywuj / wznów oferty", false),
    UPDATE_DESCRIPTION("Zaktualizuj opis i zdjęcia (szablon + magazyn)", false),
    CHANGE_TEMPLATE("Zmień szablon i zaktualizuj opis", true),
    UPDATE_TITLE("Ustaw tytuł z nazwy w magazynie", false),
    CHANGE_SHIPPING_RATE("Zmień cennik dostawy", true),
    LINK_BY_SKU("Powiąż z magazynem po sygnaturze (SKU)", false),
    FORGET("Usuń z pamięci aplikacji (nie kończy na Allegro)", false);

    private final String label;
    private final boolean needsValue;

    OfferOperation(String label, boolean needsValue) {
        this.label = label;
        this.needsValue = needsValue;
    }

    public String getLabel() { return label; }
    public boolean isNeedsValue() { return needsValue; }
}
