package com.xhhao.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xhhao.insight.storage.model.AlertIncidentCursor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AlertCursorCodecTest {

    private final AlertCursorCodec codec = new AlertCursorCodec();

    @Test
    void roundTripsAStableCursor() {
        AlertIncidentCursor cursor = new AlertIncidentCursor(42_000, "alert-42");

        assertThat(codec.decode(codec.encode(cursor))).isEqualTo(cursor);
    }

    @Test
    void rejectsOversizedAndControlCharacterIds() {
        String oversized = "7000:" + "x".repeat(129);
        String control = "7000:alert\n42";

        assertThatThrownBy(() -> codec.decode(encode(oversized)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid alert cursor");
        assertThatThrownBy(() -> codec.decode(encode(control)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid alert cursor");
    }

    @Test
    void rejectsNegativeTimestampsAndMalformedValues() {
        assertThatThrownBy(() -> codec.decode(encode("-1:alert")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid alert cursor");
        assertThatThrownBy(() -> codec.decode("not-base64"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid alert cursor");
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
