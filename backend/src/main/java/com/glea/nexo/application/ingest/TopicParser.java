package com.glea.nexo.application.ingest;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TopicParser {

    private static final Set<String> IGNORE_SEGMENTS = Set.of(
            "sensor", "actuator", "telemetry", "status", "alerts", "cmd", "state");

    public TopicParts parse(String topic) {
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("topic is required");
        }
        String[] parts = topic.split("/");
        if (parts.length < 5 || !"agro".equals(parts[0])) {
            throw new IllegalArgumentException("topic format is invalid");
        }

        String farmCode = parts[1];
        String zoneCode = parts[2];
        String type = parts[4];

        String deviceUidFromTopic = null;
        for (int i = 5; i < parts.length; i++) {
            if (!IGNORE_SEGMENTS.contains(parts[i])) {
                deviceUidFromTopic = parts[i];
                break;
            }
        }

        return new TopicParts(farmCode, zoneCode, deviceUidFromTopic, type);
    }

    public record TopicParts(
            String farmCode,
            String zoneCode,
            String deviceUidFromTopic,
            String type) {
    }
}
