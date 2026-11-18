package com.suwontrip.app.taste;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TasteVectorTests {
    @Test
    void acceptsBoundaryIntensities() {
        assertThat(new TasteVector(0, 0.5, 1).bitterness()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidValuesOnEveryAxis(double value) {
        assertThatThrownBy(() -> new TasteVector(value, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TasteVector(0, value, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TasteVector(0, 0, value)).isInstanceOf(IllegalArgumentException.class);
    }
}
