package pl.allegrolister.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import pl.allegrolister.config.AppProperties;
import pl.allegrolister.domain.OfferTemplate;
import pl.allegrolister.domain.SectionLayout;
import pl.allegrolister.domain.TemplateSection;
import pl.allegrolister.repo.OfferTemplateRepository;
import pl.allegrolister.repo.ProductRepository;

/**
 * Dane startowe: dwa szablony opisu oraz (opcjonalnie) przykładowe produkty.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final OfferTemplateRepository templates;
    private final ProductRepository products;
    private final ProductImportService importService;
    private final AppProperties props;

    public DataSeeder(OfferTemplateRepository templates, ProductRepository products,
                      ProductImportService importService, AppProperties props) {
        this.templates = templates;
        this.products = products;
        this.importService = importService;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (templates.count() == 0) {
            OfferTemplate standard = new OfferTemplate();
            standard.setName("Standardowy (zdjęcia + opis + cechy)");
            standard.setSections(new java.util.ArrayList<>(List.of(
                    new TemplateSection(SectionLayout.IMAGE_TEXT, "<h1>[nazwa]</h1>\n<p>Producent: <b>[producent]</b></p>\n[opis]",
                            "[zdjecie_1]", null),
                    new TemplateSection(SectionLayout.TEXT, "<h2>Specyfikacja</h2>\n[cechy]\n<p>Kod produktu: [sku], EAN: [ean]</p>",
                            null, null),
                    new TemplateSection(SectionLayout.IMAGE_IMAGE, null, "[zdjecie_2]", "[zdjecie_3]"))));
            templates.save(standard);

            OfferTemplate onlyDescription = new OfferTemplate();
            onlyDescription.setName("Tylko opis");
            onlyDescription.setSections(new java.util.ArrayList<>(List.of(new TemplateSection(SectionLayout.TEXT, "[opis]", null, null))));
            templates.save(onlyDescription);
            log.info("Utworzono domyślne szablony opisów");
        }
        if (props.isSeedSampleData() && products.count() == 0) {
            try (InputStream in = getClass().getResourceAsStream("/sample-products.csv")) {
                if (in != null) {
                    var result = importService.importCsv(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    log.info("Wczytano przykładowe produkty: {}", result.created());
                }
            }
        }
    }
}
