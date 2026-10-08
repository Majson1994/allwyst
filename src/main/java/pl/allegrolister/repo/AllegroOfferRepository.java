package pl.allegrolister.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pl.allegrolister.domain.AllegroOffer;

public interface AllegroOfferRepository extends JpaRepository<AllegroOffer, Long> {

    Optional<AllegroOffer> findByAccountIdAndOfferId(Long accountId, String offerId);

    List<AllegroOffer> findByAccountIdAndProductIsNotNull(Long accountId);

    List<AllegroOffer> findByAccountIdAndOfferIdIn(Long accountId, List<String> offerIds);

    long countByStatus(String status);

    List<AllegroOffer> findByProductId(Long productId);

    @Query("select o from AllegroOffer o left join o.product p where o.account.id = :accountId "
            + "and (:status = '' or o.status = :status) "
            + "and (:q = '' or lower(o.name) like lower(concat('%', :q, '%')) or o.offerId like concat('%', :q, '%') "
            + "     or lower(p.sku) like lower(concat('%', :q, '%')) or lower(o.externalId) like lower(concat('%', :q, '%')))")
    Page<AllegroOffer> search(@Param("accountId") Long accountId, @Param("status") String status,
                              @Param("q") String q, Pageable pageable);
}
