package com.suwontrip.app.recommendation;

import java.util.Optional;
import java.util.Set;

public interface MenuCardLookup {
    Optional<Card> next(PreferenceProfile profile, Set<String> excludedMenuIds);

    record Card(String id, String cafeId, String name, String description, String imageUrl,
                String imageAlt, boolean isDemo, Cafe cafe) {}
    record Cafe(String id, String name, String roadAddress, Location location, String directionsUrl) {}
    record Location(double latitude, double longitude) {}
}
