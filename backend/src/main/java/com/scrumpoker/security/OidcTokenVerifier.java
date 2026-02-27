package com.scrumpoker.security;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Verifies OIDC access tokens from Keycloak by calling the userinfo endpoint.
 * Used for WebSocket authentication where tokens are passed as query parameters.
 */
@ApplicationScoped
public class OidcTokenVerifier {

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String authServerUrl;

    @Inject
    Vertx vertx;

    private WebClient webClient;

    @PostConstruct
    void init() {
        webClient = WebClient.create(vertx);
    }

    /**
     * Verifies an OIDC access token by calling the Keycloak userinfo endpoint.
     *
     * @param token The bearer access token
     * @return Uni containing the verified user info, or failure if invalid
     */
    public Uni<OidcUser> verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Token cannot be null or blank"));
        }

        String userinfoUrl = authServerUrl + "/protocol/openid-connect/userinfo";

        return webClient.getAbs(userinfoUrl)
                .bearerTokenAuthentication(token)
                .send()
                .onItem().transform(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject body = response.bodyAsJsonObject();
                        String sub = body.getString("sub");
                        String preferredUsername = body.getString("preferred_username", "User");
                        String email = body.getString("email", "");

                        Log.debugf("OIDC token verified for user: sub=%s, username=%s", sub, preferredUsername);
                        return new OidcUser(sub, preferredUsername, email);
                    }

                    throw new SecurityException("Token verification failed: HTTP " + response.statusCode());
                })
                .onFailure().invoke(failure ->
                        Log.errorf(failure, "OIDC token verification failed"));
    }

    /**
     * Represents a verified OIDC user.
     */
    public record OidcUser(String sub, String preferredUsername, String email) {
    }
}
