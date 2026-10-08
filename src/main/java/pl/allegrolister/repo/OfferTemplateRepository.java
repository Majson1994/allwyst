package pl.allegrolister.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.allegrolister.domain.OfferTemplate;

public interface OfferTemplateRepository extends JpaRepository<OfferTemplate, Long> {

    List<OfferTemplate> findAllByOrderByNameAsc();
}
