package pl.allegrolister.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.allegrolister.domain.ListingItem;
import pl.allegrolister.domain.ListingStatus;

public interface ListingItemRepository extends JpaRepository<ListingItem, Long> {

    List<ListingItem> findByJobIdOrderByIdAsc(Long jobId);

    List<ListingItem> findByStatus(ListingStatus status);

    long countByStatus(ListingStatus status);

    boolean existsByProductId(Long productId);
}
