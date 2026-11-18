package com.suwontrip.app.taste;

public record TasteVector(double sweetness, double sourness, double bitterness) {
    public TasteVector {
        if (!valid(sweetness) || !valid(sourness) || !valid(bitterness)) {
            throw new IllegalArgumentException("taste intensity must be finite and in [0,1]");
        }
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
