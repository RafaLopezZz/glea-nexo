package com.glea.nexo.application.inventory;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

public final class PaginationUtils {

    private PaginationUtils() {
    }

    public static PageRequest buildPageRequest(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        if (!StringUtils.hasText(sort)) {
            return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        String[] tokens = sort.split(",");
        String property = tokens[0].trim();
        Sort.Direction direction = (tokens.length > 1)
                ? Sort.Direction.fromOptionalString(tokens[1].trim().toUpperCase()).orElse(Sort.Direction.ASC)
                : Sort.Direction.ASC;

        return PageRequest.of(safePage, safeSize, Sort.by(direction, property));
    }
}
