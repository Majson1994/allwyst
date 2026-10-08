package pl.allegrolister.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pl.allegrolister.repo.EventLogRepository;

@Controller
public class LogController {

    private final EventLogRepository logs;

    public LogController(EventLogRepository logs) {
        this.logs = logs;
    }

    @GetMapping("/logs")
    public String list(@RequestParam(defaultValue = "") String area, @RequestParam(defaultValue = "0") int page, Model model) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), 100);
        model.addAttribute("page", area.isBlank() ? logs.findAllByOrderByIdDesc(pr) : logs.findByAreaOrderByIdDesc(area, pr));
        model.addAttribute("area", area);
        return "logs";
    }
}
