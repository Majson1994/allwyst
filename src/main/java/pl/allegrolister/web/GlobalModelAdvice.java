package pl.allegrolister.web;

import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import pl.allegrolister.allegro.AllegroApiException;
import pl.allegrolister.domain.AllegroAccount;
import pl.allegrolister.repo.AllegroAccountRepository;
import pl.allegrolister.service.ProductTextFormat;

/**
 * Wspólne elementy kontrolerów HTML: lista kont w nawigacji, konwersja liczb z przecinkiem,
 * błędy pokazywane jako komunikat na poprzedniej stronie.
 */
@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAdvice {

    private final AllegroAccountRepository accounts;

    public GlobalModelAdvice(AllegroAccountRepository accounts) {
        this.accounts = accounts;
    }

    @ModelAttribute("allAccounts")
    public List<AllegroAccount> allAccounts() {
        return accounts.findByActiveTrueOrderByIdAsc();
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // musi być wywołane przed innymi ustawieniami bindera; formularz wystawiania może mieć setki pozycji
        binder.setAutoGrowCollectionLimit(5000);
        if (binder.getTarget() == null) {
            // binder dla @RequestParam - bez edytorów, żeby defaultValue = "" nie zamieniało się w null
            return;
        }
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
        binder.registerCustomEditor(BigDecimal.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(ProductTextFormat.parseDecimal(text));
            }
        });
    }

    @ExceptionHandler({AllegroApiException.class, IllegalArgumentException.class, IllegalStateException.class})
    public String handle(RuntimeException e, HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", e.getMessage());
        return "redirect:" + safeBackPath(request.getHeader("Referer"));
    }

    /** Ścieżka powrotu tylko w obrębie aplikacji (bez otwartych przekierowań). */
    static String safeBackPath(String referer) {
        if (referer == null || referer.isBlank()) {
            return "/";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
            if (!path.startsWith("/") || path.startsWith("//")) {
                return "/";
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException ex) {
            return "/";
        }
    }
}
