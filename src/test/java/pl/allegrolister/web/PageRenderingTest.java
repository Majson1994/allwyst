package pl.allegrolister.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pl.allegrolister.allegro.AllegroCatalogService;
import pl.allegrolister.allegro.AllegroEnvironment;
import pl.allegrolister.allegro.AllegroSellerService;
import pl.allegrolister.allegro.model.NamedRef;
import pl.allegrolister.domain.AllegroAccount;
import pl.allegrolister.domain.ListingItem;
import pl.allegrolister.domain.ListingJob;
import pl.allegrolister.domain.Product;
import pl.allegrolister.repo.AllegroAccountRepository;
import pl.allegrolister.repo.ListingItemRepository;
import pl.allegrolister.repo.ProductRepository;
import pl.allegrolister.service.ListingService;

/**
 * Renderuje wszystkie strony panelu na bazie H2 w pamięci (bez połączenia z Allegro),
 * żeby wychwycić błędy w szablonach Thymeleaf i wiązaniu formularzy.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pages;DB_CLOSE_DELAY=-1",
        "app.sync.enabled=false",
        "app.seed-sample-data=true"
})
@AutoConfigureMockMvc
class PageRenderingTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AllegroAccountRepository accounts;
    @Autowired
    private ProductRepository products;
    @Autowired
    private ListingItemRepository items;
    @Autowired
    private ListingService listingService;

    @MockitoBean
    private AllegroSellerService seller;
    @MockitoBean
    private AllegroCatalogService catalog;

    @BeforeEach
    void stubAllegro() {
        AllegroSellerService.SellerDictionaries dicts = new AllegroSellerService.SellerDictionaries();
        dicts.shippingRates = List.of(new NamedRef("rate-1", "Cennik standardowy"));
        dicts.returnPolicies = List.of(new NamedRef("ret-1", "Zwroty 14 dni"));
        when(seller.dictionaries(any())).thenReturn(dicts);
    }

    @Test
    void staticPagesRender() throws Exception {
        for (String url : List.of("/", "/products", "/products/new", "/templates", "/templates/new", "/mappings",
                "/listing", "/accounts", "/logs", "/offers", "/products/sample.csv")) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
        Product first = products.findAll().get(0);
        mvc.perform(get("/products/" + first.getId())).andExpect(status().isOk());
        mvc.perform(get("/templates/1")).andExpect(status().isOk());
        mvc.perform(get("/templates/1/preview")).andExpect(status().isOk());
    }

    @Test
    void listingFlowPagesRender() throws Exception {
        AllegroAccount a = new AllegroAccount();
        a.setLogin("test-seller");
        a.setSellerId("123");
        a.setEnvironment(AllegroEnvironment.SANDBOX);
        a.setAccessToken("token");
        a.setRefreshToken("refresh");
        a.setTokenExpiresAt(Instant.now().plusSeconds(3600));
        a = accounts.save(a);

        List<Long> productIds = products.findAll().stream().map(Product::getId).toList();
        ListingJob job = listingService.createJob(a.getId(), productIds);

        mvc.perform(get("/listing/" + job.getId())).andExpect(status().isOk());
        mvc.perform(post("/listing/" + job.getId())
                        .param("action", "bulk")
                        .param("bulkField", "category")
                        .param("bulkValue", "257931")
                        .param("bulkLabel", "Testowa kategoria"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/listing/" + job.getId())).andExpect(status().isOk());

        ListingItem item = items.findByJobIdOrderByIdAsc(job.getId()).get(0);
        mvc.perform(get("/listing/item/" + item.getId() + "/parameters")).andExpect(status().isOk());
        mvc.perform(get("/listing/item/" + item.getId() + "/preview")).andExpect(status().isOk());
        mvc.perform(get("/accounts/" + a.getId() + "/settings")).andExpect(status().isOk());
        mvc.perform(get("/offers?accountId=" + a.getId())).andExpect(status().isOk());
        mvc.perform(get("/api/listing/" + job.getId() + "/status")).andExpect(status().isOk());
    }
}
