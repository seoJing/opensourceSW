package com.suwontrip.app.catalog.persistence;

import java.nio.charset.StandardCharsets;

public final class CatalogDatabaseFixture {
    private CatalogDatabaseFixture() {
    }

    public static byte[] document(String version) {
        return """
                {
                  "schemaVersion": 1,
                  "dataVersion": "%s",
                  "cafes": [
                    {
                      "id": "cafe-1", "name": "검증 카페", "roadAddress": "테스트 주소",
                      "location": {"latitude": 37.28, "longitude": 127.01},
                      "source": {"url": "https://example.invalid/cafe/1", "externalId": "place-1", "observedOn": "2020-01-01"}
                    },
                    {
                      "id": "cafe-2", "name": "두 번째 카페", "roadAddress": "두 번째 주소",
                      "source": {"url": "https://example.invalid/cafe/2", "externalId": "place-2", "observedOn": "2020-01-01"}
                    }
                  ],
                  "menus": [
                    {
                      "id": "menu-1", "cafeId": "cafe-1", "name": "라테",
                      "description": "테스트 음료", "priceKrw": 4500, "isDemo": true,
                      "availability": "AVAILABLE",
                      "saleConfirmation": {"confirmedOn": "2020-01-02", "reference": "검증용 확인 기록"},
                      "source": {"url": "https://example.invalid/menu/1", "externalId": "item-1", "observedOn": "2020-01-01"}
                    },
                    {
                      "id": "menu-2", "cafeId": "cafe-1", "name": "가격 미확인 음료", "isDemo": false,
                      "availability": "UNKNOWN",
                      "source": {"url": "https://example.invalid/menu/2", "externalId": "item-2", "observedOn": "2020-01-01"}
                    },
                    {
                      "id": "menu-3", "cafeId": "cafe-2", "name": "시즌 메뉴", "priceKrw": 0, "isDemo": false,
                      "availability": "SEASON_ENDED",
                      "source": {"url": "https://example.invalid/menu/3", "externalId": "item-3", "observedOn": "2020-01-01"}
                    }
                  ]
                }
                """.formatted(version).getBytes(StandardCharsets.UTF_8);
    }
}
