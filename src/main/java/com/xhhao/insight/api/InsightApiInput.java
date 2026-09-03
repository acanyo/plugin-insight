package com.xhhao.insight.api;

import org.springframework.web.reactive.function.server.ServerRequest;

/** Applies uniform length and control-character limits to custom endpoint inputs. */
final class InsightApiInput {

    static final int ENUM_MAX_LENGTH = 32;
    static final int FILTER_MAX_LENGTH = 64;
    static final int CURSOR_MAX_LENGTH = 512;
    static final int RESOURCE_ID_MAX_LENGTH = 128;

    private InsightApiInput() {
    }

    static String query(
        ServerRequest request,
        String name,
        String defaultValue,
        int maximumLength
    ) {
        String value = optionalQuery(request, name, maximumLength);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    static String optionalQuery(ServerRequest request, String name, int maximumLength) {
        return request.queryParam(name)
            .map(value -> validate(name, value, maximumLength))
            .orElse(null);
    }

    static int integerQuery(ServerRequest request, String name, int defaultValue) {
        String value = optionalQuery(request, name, 11);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
    }

    static String pathVariable(ServerRequest request, String name, int maximumLength) {
        return validate(name, request.pathVariable(name), maximumLength);
    }

    private static String validate(String name, String value, int maximumLength) {
        if (maximumLength <= 0) {
            throw new IllegalArgumentException("maximumLength must be positive");
        }
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " exceeds the maximum length");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " contains control characters");
        }
        return value;
    }
}
