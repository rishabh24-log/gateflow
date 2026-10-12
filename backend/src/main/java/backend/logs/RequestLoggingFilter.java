package backend.logs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final RequestLogRepository repository;

    public RequestLoggingFilter(RequestLogRepository repository) {
        this.repository = repository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/admin/requests")
            || path.startsWith("/actuator")
            || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            RequestLogEntity log = new RequestLogEntity();
            log.setRequestId("req_" + UUID.randomUUID().toString().substring(0, 8));
            log.setMethod(request.getMethod());
            log.setPath(request.getRequestURI());
            log.setStatus(response.getStatus());
            log.setLatencyMs((int) ((System.nanoTime() - started) / 1_000_000));
            Object prefix = request.getAttribute("apiKeyPrefix");
            if (prefix != null) {
                log.setApiKeyPrefix(prefix.toString());
            }
            try {
                repository.save(log);
            } catch (Exception ignored) {
                // Never fail a client request because logging failed.
            }
        }
    }
}
