package pl.allegrolister.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.allegrolister.domain.ParameterRule;

public interface ParameterRuleRepository extends JpaRepository<ParameterRule, Long> {

    List<ParameterRule> findByEnabledTrue();

    List<ParameterRule> findAllByOrderByPriorityAscIdAsc();
}
