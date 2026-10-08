package pl.allegrolister.repo;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import pl.allegrolister.domain.EventLog;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    List<EventLog> findTop10ByOrderByIdDesc();

    Page<EventLog> findAllByOrderByIdDesc(Pageable pageable);

    Page<EventLog> findByAreaOrderByIdDesc(String area, Pageable pageable);
}
