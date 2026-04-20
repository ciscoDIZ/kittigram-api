package es.kitti.gateway.config;

import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class WebClientConfig {

    @Inject
    Vertx vertx;

    @Produces
    @ApplicationScoped
    public WebClient webClient() {
        return WebClient.create(vertx);
    }
}