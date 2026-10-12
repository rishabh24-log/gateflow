package backend.api;

import backend.auth.JwtService;
import backend.config.GateflowProperties;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final GateflowProperties properties;

    public AuthController(JwtService jwtService, GateflowProperties properties) {
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody LoginRequest request) {
        if (request.username() == null || request.password() == null
            || !request.username().equals(properties.getAdminUsername())
            || !request.password().equals(properties.getAdminPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return Map.of("token", jwtService.createToken(request.username()), "username", request.username());
    }

    public record LoginRequest(String username, String password) {}
}
