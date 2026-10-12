package backend.api;

import backend.config.GateflowProperties;
import backend.keys.ApiKeyService;
import backend.logs.RequestLogEntity;
import backend.logs.RequestLogRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ApiKeyService apiKeyService;
    private final RequestLogRepository requestLogs;
    private final GateflowProperties properties;

    public AdminController(
        ApiKeyService apiKeyService,
        RequestLogRepository requestLogs,
        GateflowProperties properties
    ) {
        this.apiKeyService = apiKeyService;
        this.requestLogs = requestLogs;
        this.properties = properties;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        long total = requestLogs.count();
        long success = requestLogs.countByStatusBetween(200, 399);
        long blocked = requestLogs.countByStatus(429);
        Double avg = requestLogs.averageLatency();
        double successRate = total == 0 ? 100 : (success * 100.0 / total);
        return Map.of(
            "totalRequests", total,
            "successRate", Math.round(successRate * 10) / 10.0,
            "avgLatencyMs", avg == null ? 0 : Math.round(avg),
            "blockedRequests", blocked,
            "activeRoutes", 2
        );
    }

    @GetMapping("/traffic")
    public List<Integer> traffic() {
        Instant start = Instant.now().minus(12, ChronoUnit.HOURS);
        List<RequestLogEntity> rows = requestLogs.findByCreatedAtAfterOrderByCreatedAtDesc(start, PageRequest.of(0, 2000));
        int[] buckets = new int[12];
        long now = System.currentTimeMillis();
        for (RequestLogEntity row : rows) {
            long ageHours = (now - row.getCreatedAt().toEpochMilli()) / 3_600_000;
            int index = 11 - (int) Math.min(11, Math.max(0, ageHours));
            buckets[index]++;
        }
        List<Integer> result = new ArrayList<>();
        int max = 1;
        for (int bucket : buckets) {
            max = Math.max(max, bucket);
        }
        for (int bucket : buckets) {
            result.add((int) Math.round(bucket * 100.0 / max));
        }
        return result;
    }

    @GetMapping("/requests")
    public List<Map<String, Object>> requests() {
        return requestLogs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)).stream()
            .map(row -> Map.<String, Object>of(
                "id", row.getRequestId(),
                "method", row.getMethod(),
                "path", row.getPath(),
                "status", row.getStatus(),
                "latencyMs", row.getLatencyMs(),
                "time", row.getCreatedAt().toString(),
                "apiKeyPrefix", row.getApiKeyPrefix() == null ? "" : row.getApiKeyPrefix()
            ))
            .toList();
    }

    @GetMapping("/keys")
    public List<Map<String, Object>> keys() {
        return apiKeyService.list().stream()
            .map(key -> Map.<String, Object>of(
                "id", key.getId(),
                "name", key.getName(),
                "prefix", key.getPrefix(),
                "quotaPerMinute", key.getQuotaPerMinute(),
                "revoked", key.isRevoked(),
                "createdAt", key.getCreatedAt().toString()
            ))
            .toList();
    }

    @PostMapping("/keys")
    public ApiKeyService.CreatedApiKey createKey(@RequestBody CreateKeyRequest request) {
        int quota = request.quotaPerMinute() == null ? 30 : request.quotaPerMinute();
        return apiKeyService.create(request.name() == null ? "Untitled key" : request.name(), quota);
    }

    @DeleteMapping("/keys/{id}")
    public void revokeKey(@PathVariable Long id) {
        apiKeyService.revoke(id);
    }

    @GetMapping("/routes")
    public List<Map<String, String>> routes() {
        return List.of(
            Map.of("name", "Usage API", "match", "/usage/**", "target", properties.getUsageUrl(), "auth", "API key"),
            Map.of("name", "Admin API", "match", "/api/admin/**", "target", "GateFlow", "auth", "JWT")
        );
    }

    @GetMapping(value = "/usage", produces = "application/json")
    public String usage() throws Exception {
        var request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(properties.getUsageUrl() + "/usage"))
            .timeout(java.time.Duration.ofSeconds(3))
            .GET()
            .build();
        return java.net.http.HttpClient.newHttpClient()
            .send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            .body();
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return Map.of(
            "application", "gateflow",
            "usageUrl", properties.getUsageUrl(),
            "activeApiKeys", apiKeyService.activeCount(),
            "rateLimit", "Redis with local fallback"
        );
    }

    public record CreateKeyRequest(String name, Integer quotaPerMinute) {}
}
