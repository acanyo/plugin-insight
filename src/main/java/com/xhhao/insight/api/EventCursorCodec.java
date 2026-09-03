package com.xhhao.insight.api;

import com.xhhao.insight.storage.model.MonitorEventCursor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Encodes opaque event pagination cursors and rejects malformed or oversized input. */
@Component
public class EventCursorCodec {

    private static final int MAX_ENCODED_LENGTH = 512;
    private static final int MAX_ID_LENGTH = 128;

    public String encode(MonitorEventCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String raw = cursor.occurredAtMs() + ":" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public MonitorEventCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        if (cursor.length() > MAX_ENCODED_LENGTH) {
            throw invalidCursor(null);
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf(':');
            if (separator <= 0 || separator == raw.length() - 1) {
                throw invalidCursor(null);
            }
            String id = raw.substring(separator + 1);
            if (id.length() > MAX_ID_LENGTH || containsControlCharacter(id)) {
                throw invalidCursor(null);
            }
            return new MonitorEventCursor(Long.parseLong(raw.substring(0, separator)), id);
        } catch (IllegalArgumentException error) {
            if ("Invalid event cursor".equals(error.getMessage())) {
                throw error;
            }
            throw invalidCursor(error);
        }
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static IllegalArgumentException invalidCursor(Throwable cause) {
        return new IllegalArgumentException("Invalid event cursor", cause);
    }
}
