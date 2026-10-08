package pl.allegrolister.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pl.allegrolister.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    /** Puste stringi = brak filtra (unikamy ":param is null", które psuje się na PostgreSQL). */
    @Query("select p from Product p where "
            + "(:q = '' or lower(p.name) like lower(concat('%', :q, '%')) "
            + "   or lower(p.sku) like lower(concat('%', :q, '%')) or p.ean like concat('%', :q, '%')) "
            + "and (:cat = '' or p.shopCategory = :cat)")
    Page<Product> search(@Param("q") String q, @Param("cat") String cat, Pageable pageable);

    @Query("select distinct p.shopCategory from Product p where p.shopCategory is not null order by p.shopCategory")
    List<String> findDistinctShopCategories();
}
