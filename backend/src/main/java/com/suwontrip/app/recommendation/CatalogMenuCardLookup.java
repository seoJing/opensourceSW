package com.suwontrip.app.recommendation;

import com.suwontrip.app.catalog.CatalogCafe;
import com.suwontrip.app.catalog.persistence.CatalogRepository;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class CatalogMenuCardLookup implements MenuCardLookup {
    private final CatalogRepository catalog;
    private final MenuRecommendationRepository candidates;

    public CatalogMenuCardLookup(CatalogRepository catalog, MenuRecommendationRepository candidates) {
        this.catalog = catalog;
        this.candidates = candidates;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<Card> next(PreferenceProfile profile, Set<String> excludedMenuIds) {
        var active = catalog.activeDataset().orElseThrow(() -> new DataRetrievalFailureException("No active catalog"));
        return candidates.first(active.dataVersion(), excludedMenuIds, profile).map(id -> {
            var menu = catalog.findMenu(id).orElseThrow(() -> new IllegalStateException("Ranked menu missing"));
            var cafe = catalog.findCafe(menu.cafeId()).orElseThrow(() -> new IllegalStateException("Menu cafe missing"));
            var location = cafe.location() == null ? null
                    : new Location(cafe.location().latitude(), cafe.location().longitude());
            return new Card(id, cafe.id(), menu.name(), menu.description() == null ? "" : menu.description(),
                    null, menu.name(), menu.isDemo(), new Cafe(cafe.id(), cafe.name(), cafe.roadAddress(),
                    location, directions(cafe)));
        });
    }

    private static String directions(CatalogCafe cafe) {
        if (cafe.location() == null || Math.abs(cafe.location().latitude()) > 85.05112878) {
            return null;
        }
        double x = Math.toRadians(cafe.location().longitude()) * 6378137;
        double latitude = Math.toRadians(cafe.location().latitude());
        double y = 6378137 * Math.log(Math.tan(Math.PI / 4 + latitude / 2));
        return String.format(Locale.ROOT, "https://map.naver.com/p/directions/-/%.4f,%.4f,%s,,ADDRESS_POI/-/walk",
                x, y, UriUtils.encode(cafe.name(), StandardCharsets.UTF_8));
    }
}
