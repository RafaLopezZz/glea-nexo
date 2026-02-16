package com.glea.nexo.api.error;

public record ApiFieldError(
        String field,
        String message
) {
}
