package com.glea.nexo.application.common;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

@Component
public class TimeRangeValidator {

    public void validate(Instant from, Instant to) {
        if (from == null || to == null) {
            return;
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
        Instant maxTo = ZonedDateTime.ofInstant(from, ZoneOffset.UTC)
                .plusYears(2)
                .toInstant();
        if (to.isAfter(maxTo)) {
            throw new IllegalArgumentException("time range must not exceed 2 years");
        }
    }
}
