package pl.allegrolister.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/**
 * Pomocnicze funkcje dla widoków Thymeleaf, dostępne jako ${@fmt.metoda(...)}.
 */
@Component("fmt")
public class ViewHelpers {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Europe/Warsaw"));

    public String dt(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }

    public String money(BigDecimal amount) {
        return amount == null ? "—" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " zł";
    }

    public String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /** Klasa CSS odznaki statusu (statusy wystawiania i ofert). */
    public String statusClass(Object status) {
        if (status == null) {
            return "";
        }
        return switch (status.toString()) {
            case "ACTIVE" -> "ok";
            case "ERROR" -> "err";
            case "ENDED" -> "muted";
            case "PENDING", "PROCESSING", "QUEUED", "ACTIVATING" -> "busy";
            case "INACTIVE" -> "warn";
            default -> "";
        };
    }

    public String offerStatusLabel(String status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case "ACTIVE" -> "Aktywna";
            case "INACTIVE" -> "Szkic";
            case "ACTIVATING" -> "Aktywowanie";
            case "ENDED" -> "Zakończona";
            default -> status;
        };
    }
}
