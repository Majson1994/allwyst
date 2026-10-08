package pl.allegrolister.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pl.allegrolister.domain.EventLog;
import pl.allegrolister.repo.EventLogRepository;

/**
 * Zapis zdarzeń do dziennika (widoczny w zakładce Logi). Zapis w osobnej transakcji,
 * żeby log przetrwał wycofanie operacji, której dotyczy.
 */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class EventLogService {

    private static final Logger log = LoggerFactory.getLogger(EventLogService.class);

    private final EventLogRepository repo;

    public EventLogService(EventLogRepository repo) {
        this.repo = repo;
    }

    public void info(String area, Long accountId, String message) {
        write("INFO", area, accountId, message, null);
    }

    public void warn(String area, Long accountId, String message, String details) {
        write("WARN", area, accountId, message, details);
    }

    public void error(String area, Long accountId, String message, String details) {
        write("ERROR", area, accountId, message, details);
    }

    public void write(String level, String area, Long accountId, String message, String details) {
        try {
            EventLog e = new EventLog();
            e.setLevel(level);
            e.setArea(area);
            e.setAccountId(accountId);
            e.setMessage(cut(message, 2000));
            e.setDetails(cut(details, 20000));
            repo.save(e);
        } catch (RuntimeException ex) {
            log.warn("Nie udało się zapisać logu: {} ({})", message, ex.getMessage());
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
