package pl.allegrolister.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.allegrolister.domain.ListingJob;

public interface ListingJobRepository extends JpaRepository<ListingJob, Long> {

    List<ListingJob> findTop50ByOrderByIdDesc();
}
