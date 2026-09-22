package com.fuzis.tickets.config;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

@Provider
public class JsonbProvider implements ContextResolver<Jsonb> {
    private final Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withNullValues(true));

    @Override
    public Jsonb getContext(Class<?> type) {
        return jsonb;
    }
}
