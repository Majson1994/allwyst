package pl.allegrolister.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.allegrolister.domain.CategoryMapping;

public interface CategoryMappingRepository extends JpaRepository<CategoryMapping, Long> {

    Optional<CategoryMapping> findByShopCategory(String shopCategory);

    List<CategoryMapping> findAllByOrderByShopCategoryAsc();
}
