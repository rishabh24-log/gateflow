package backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateflow")
public class GateflowProperties {

    private String usageUrl = "http://localhost:8082";
    private String jwtSecret = "gateflow-portfolio-secret-key-change-me-32";
    private String adminUsername = "admin";
    private String adminPassword = "gateflow";
    private boolean rateLimitFallbackLocal = true;

    public String getUsageUrl() {
        return usageUrl;
    }

    public void setUsageUrl(String usageUrl) {
        this.usageUrl = usageUrl;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public boolean isRateLimitFallbackLocal() {
        return rateLimitFallbackLocal;
    }

    public void setRateLimitFallbackLocal(boolean rateLimitFallbackLocal) {
        this.rateLimitFallbackLocal = rateLimitFallbackLocal;
    }
}
