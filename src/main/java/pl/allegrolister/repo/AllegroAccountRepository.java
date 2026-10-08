package pl.allegrolister.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.allegrolister.allegro.AllegroEnvironment;
import pl.allegrolister.domain.AllegroAccount;

public interface AllegroAccountRepository extends JpaRepository<AllegroAccount, Long> {

    Optional<AllegroAccount> findByEnvironmentAndSellerId(AllegroEnvironment environment, String sellerId);

    List<AllegroAccount> findByActiveTrueOrderByIdAsc();
}
