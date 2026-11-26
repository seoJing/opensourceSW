package com.suwontrip.app.recommendation;

public record PreferenceProfile(
        MenuType menuType,
        Level priceLevel,
        Level sweetness,
        Boolean coffeeBased,
        Temperature temperature,
        Boolean signature
) {
    public enum MenuType { BEVERAGE, DESSERT, MEAL }
    public enum Level { LOW, MEDIUM, HIGH }
    public enum Temperature { HOT, COLD }
}
