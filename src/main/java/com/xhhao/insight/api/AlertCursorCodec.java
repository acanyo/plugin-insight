package com.xhhao.insight.api;

import com.xhhao.insight.storage.model.AlertIncidentCursor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Encodes opaque alert cursors and rejects malformed or oversized input. */
public class AlertCursorCodec {

    private static final int MAX_ENCODED_LENGTH = 512;
    private static final int MAX_ID_LENGTH = 128;

    public String encode(AlertIncidentCursor cursor) {
        if (cursor == null) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            (cursor.updatedAtMs() + ":" + cursor.id()).getBytes(StandardCharsets.UTF_8));
    }

    public AlertIncidentCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        if (cursor.length() > MAX_ENCODED_LENGTH) {
            throw invalidCursor();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf(':');
            if (separator <= 0 || separator == raw.length() - 1) {
                throw invalidCursor();
            }
            String id = raw.substring(separator + 1);
            if (id.length() > MAX_ID_LENGTH || containsControlCharacter(id)) {
                throw invalidCursor();
            }
            return new AlertIncidentCursor(
                Long.parseLong(raw.substring(0, separator)), id);
        } catch (IllegalArgumentException error) {
            if ("Invalid alert cursor".equals(error.getMessage())) {
                throw error;
            }
            throw new IllegalArgumentException("Invalid alert cursor", error);
        }
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("Invalid alert cursor");
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
