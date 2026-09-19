package com.busgo.common.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ApiError(String code, String message, Object details,
        @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime timestamp) {
    public static ApiError of(String code, String message, Object details) {
        return new ApiError(code, message, details, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
