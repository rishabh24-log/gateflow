package backend.keys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyService {

    private final ApiKeyRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    public CreatedApiKey create(String name, int quotaPerMinute) {
        String raw = "gf_" + randomHex(24);
        ApiKey key = new ApiKey();
        key.setName(name);
        key.setKeyHash(hash(raw));
        key.setPrefix(raw.substring(0, 10));
        key.setQuotaPerMinute(quotaPerMinute);
        repository.save(key);
        return new CreatedApiKey(key.getId(), key.getName(), raw, key.getPrefix(), key.getQuotaPerMinute());
    }

    public List<ApiKey> list() {
        return repository.findAll();
    }

    public void revoke(Long id) {
        ApiKey key = repository.findById(id).orElseThrow();
        key.setRevoked(true);
        repository.save(key);
    }

    public ApiKey authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        return repository.findByKeyHash(hash(rawKey))
            .filter(key -> !key.isRevoked())
            .orElse(null);
    }

    public long activeCount() {
        return repository.countByRevokedFalse();
    }

    public static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash API key", ex);
        }
    }

    private String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    public record CreatedApiKey(Long id, String name, String key, String prefix, int quotaPerMinute) {}
}
