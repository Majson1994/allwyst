package pl.allegrolister.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import pl.allegrolister.domain.AccountSettings;

/**
 * Cena na Allegro = cena z magazynu * mnożnik + dodatek (jak w ustawieniach ofert BaseLinkera).
 */
public final class PriceCalculator {

    private PriceCalculator() {
    }

    public static BigDecimal allegroPrice(BigDecimal basePrice, AccountSettings s) {
        if (basePrice == null) {
            return null;
        }
        BigDecimal multiplier = s.getPriceMultiplier() == null ? BigDecimal.ONE : s.getPriceMultiplier();
        BigDecimal addition = s.getPriceAddition() == null ? BigDecimal.ZERO : s.getPriceAddition();
        return basePrice.multiply(multiplier).add(addition).setScale(2, RoundingMode.HALF_UP);
    }
}
