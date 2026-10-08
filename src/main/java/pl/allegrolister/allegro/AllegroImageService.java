package pl.allegrolister.allegro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import pl.allegrolister.domain.AllegroAccount;

/**
 * Wgrywanie zdjęć na serwery Allegro (POST upload.allegro.pl/sale/images z adresem URL).
 * Zdjęcia w opisie oferty muszą pochodzić z serwerów Allegro, dlatego wgrywamy je przed wystawieniem.
 */
@Service
public class AllegroImageService {

    private static final Logger log = LoggerFactory.getLogger(AllegroImageService.class);
    public static final int MAX_IMAGES = 16;

    private final AllegroClient client;

    public AllegroImageService(AllegroClient client) {
        this.client = client;
    }

    /**
     * @param urls       wgrane zdjęcia (galeria), bez błędnych
     * @param positional lista tej samej długości co wejście - null tam, gdzie wgranie się nie udało
     *                   (dzięki temu [zdjecie_3] w szablonie zawsze wskazuje 3. zdjęcie produktu)
     */
    public record UploadResult(List<String> urls, List<String> positional, List<String> errors) {
    }

    public String upload(AllegroAccount account, String sourceUrl) {
        if (sourceUrl.contains("allegroimg.com") || sourceUrl.contains("allegroimg.allegrosandbox.pl")) {
            return sourceUrl; // już na serwerach Allegro
        }
        ApiResponse r = client.upload(account, "/sale/images", Map.of("url", sourceUrl));
        if (!r.isSuccess() || r.body() == null || !r.body().hasNonNull("location")) {
            throw AllegroApiException.from(r);
        }
        return r.body().path("location").asText();
    }

    /** Wgrywa listę zdjęć; błędne pomija i raportuje. Kolejność zostaje zachowana. */
    public UploadResult uploadAll(AllegroAccount account, List<String> sourceUrls) {
        List<String> urls = new ArrayList<>();
        List<String> positional = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String src : sourceUrls) {
            if (src == null || src.isBlank()) {
                positional.add(null);
                continue;
            }
            if (urls.size() >= MAX_IMAGES) {
                errors.add("Pominięto zdjęcia powyżej limitu " + MAX_IMAGES);
                break;
            }
            try {
                String url = upload(account, src.trim());
                urls.add(url);
                positional.add(url);
            } catch (AllegroApiException e) {
                log.warn("Nie udało się wgrać zdjęcia {}: {}", src, e.getMessage());
                errors.add("Zdjęcie " + src + ": " + e.getMessage());
                positional.add(null);
            }
        }
        return new UploadResult(urls, positional, errors);
    }
}
