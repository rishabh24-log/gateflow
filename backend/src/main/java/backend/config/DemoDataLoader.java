package backend.config;

import backend.keys.ApiKeyRepository;
import backend.keys.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final ApiKeyRepository repository;
    private final ApiKeyService apiKeyService;

    public DemoDataLoader(ApiKeyRepository repository, ApiKeyService apiKeyService) {
        this.repository = repository;
        this.apiKeyService = apiKeyService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        var created = apiKeyService.create("Demo dashboard key", 30);
        log.info("Created demo API key (shown once): {}", created.key());
    }
}
