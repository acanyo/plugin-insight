package com.xhhao.insight.storage.repository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class QueryRangeValidatorTest {

    @Test
    void acceptsValidRangesWithAndWithoutLimits() {
        assertThatCode(() -> QueryRangeValidator.validate(0, 1))
            .doesNotThrowAnyException();
        assertThatCode(() -> QueryRangeValidator.validate(0, 1, 1))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsNegativeEmptyAndReversedRanges() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> QueryRangeValidator.validate(-1, 1));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> QueryRangeValidator.validate(1, 1));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> QueryRangeValidator.validate(2, 1));
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> QueryRangeValidator.validate(0, 1, 0))
            .withMessage("limit must be positive");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> QueryRangeValidator.validate(0, 1, -1))
            .withMessage("limit must be positive");
    }
}
