package backend.gateway;

import backend.config.GateflowProperties;
import backend.keys.ApiKey;
import backend.keys.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(20)
public class GatewayProxyFilter extends OncePerRequestFilter {

    private static final Set<String> HOP_BY_HOP = Set.of(
        "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
        "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private final ApiKeyService apiKeyService;
    private final RateLimitService rateLimitService;
    private final CircuitBreaker circuitBreaker;
    private final GateflowProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    public GatewayProxyFilter(
        ApiKeyService apiKeyService,
        RateLimitService rateLimitService,
        CircuitBreaker circuitBreaker,
        GateflowProperties properties
    ) {
        this.apiKeyService = apiKeyService;
        this.rateLimitService = rateLimitService;
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/usage");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        String rawKey = request.getHeader("X-API-Key");
        ApiKey apiKey = apiKeyService.authenticate(rawKey);
        if (apiKey == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Missing or invalid API key");
            return;
        }
        request.setAttribute("apiKeyPrefix", apiKey.getPrefix());

        if (!rateLimitService.allow(apiKey)) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
            return;
        }

        if (circuitBreaker.isOpen()) {
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE, "Usage circuit is open");
            return;
        }

        try {
            byte[] body = request.getInputStream().readAllBytes();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.getUsageUrl() + request.getRequestURI() + query(request)))
                .timeout(Duration.ofSeconds(3))
                .method(request.getMethod(), body.length == 0
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(body));

            Enumeration<String> names = request.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if (!HOP_BY_HOP.contains(name.toLowerCase()) && !"x-api-key".equalsIgnoreCase(name)) {
                    builder.header(name, request.getHeader(name));
                }
            }
            builder.header("X-GateFlow-Key", apiKey.getPrefix());

            HttpResponse<byte[]> upstream = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (upstream.statusCode() >= 500) {
                circuitBreaker.recordFailure();
            } else {
                circuitBreaker.recordSuccess();
            }
            response.setStatus(upstream.statusCode());
            upstream.headers().map().forEach((name, values) -> {
                if (!HOP_BY_HOP.contains(name.toLowerCase())) {
                    values.forEach(value -> response.addHeader(name, value));
                }
            });
            response.getOutputStream().write(upstream.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            circuitBreaker.recordFailure();
            writeError(response, HttpStatus.GATEWAY_TIMEOUT, "Upstream interrupted");
        } catch (Exception ex) {
            circuitBreaker.recordFailure();
            writeError(response, HttpStatus.BAD_GATEWAY, "Upstream unavailable");
        }
    }

    private String query(HttpServletRequest request) {
        return request.getQueryString() == null ? "" : "?" + request.getQueryString();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
