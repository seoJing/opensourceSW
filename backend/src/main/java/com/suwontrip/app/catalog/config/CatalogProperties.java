package com.suwontrip.app.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.catalog")
public record CatalogProperties(String path) {
}
