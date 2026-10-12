package usage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/usage")
public class UsageController {

    private final AtomicLong ids = new AtomicLong(3);
    private final List<Map<String, Object>> reports = new CopyOnWriteArrayList<>(List.of(
        Map.of("id", 1, "consumer", "Partner A", "route", "/v1/payments", "requests", 1280, "period", "today"),
        Map.of("id", 2, "consumer", "Mobile app", "route", "/v1/users", "requests", 540, "period", "today"),
        Map.of("id", 3, "consumer", "Internal jobs", "route", "/v1/webhooks", "requests", 96, "period", "today")
    ));

    @GetMapping
    public List<Map<String, Object>> list() {
        return reports;
    }

    @GetMapping("/{id}")
    public Map<String, Object> one(@PathVariable long id) {
        return reports.stream()
            .filter(row -> ((Number) row.get("id")).longValue() == id)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usage report not found"));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> row = Map.of(
            "id", ids.incrementAndGet(),
            "consumer", String.valueOf(body.getOrDefault("consumer", "New consumer")),
            "route", String.valueOf(body.getOrDefault("route", "/v1/unknown")),
            "requests", body.get("requests") instanceof Number number ? number.intValue() : 1,
            "period", "today"
        );
        reports.add(row);
        return row;
    }

    @GetMapping("/fail")
    public void fail() {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Simulated usage-service failure");
    }
}
