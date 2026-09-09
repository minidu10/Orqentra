package com.orqentra.gateway.proxy;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.orqentra.gateway.config.GatewayProperties;

/** Resolves a request path to a downstream base URL, longest prefix first. */
@Component
public class ProxyRoutes {

    private final Map<String, String> routes;

    public ProxyRoutes(GatewayProperties properties) {
        this.routes = properties.routes();
    }

    public Optional<String> targetFor(String path) {
        return routes.entrySet().stream()
                .filter(entry -> path.equals(entry.getKey()) || path.startsWith(entry.getKey() + "/"))
                .max(Comparator.comparingInt(entry -> entry.getKey().length()))
                .map(Map.Entry::getValue);
    }
}
