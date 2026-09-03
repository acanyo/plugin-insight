package com.xhhao.insight.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/** Encodes typed event details without depending on a host-provided ObjectMapper bean. */
@Slf4j
final class EventDetailsEncoder {

    private static final ObjectMapper JSON = new ObjectMapper();

    private EventDetailsEncoder() {}

    static String encode(Object details) {
        try {
            return JSON.writeValueAsString(details);
        } catch (JsonProcessingException error) {
            log.warn("Failed to encode Insight event details of type {}",
                details.getClass().getName(), error);
            return null;
        }
    }
}
