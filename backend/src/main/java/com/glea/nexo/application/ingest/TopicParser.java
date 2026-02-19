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

        String[] parts = topic.trim().split("/");

        // mínimo: agro/farm/zone/...
        if (parts.length < 4 || !"agro".equals(parts[0])) {
            throw new IllegalArgumentException("topic format is invalid: " + topic);
        }

        String farmCode = parts[1];
        String zoneCode = parts[2];

        // Buscamos el segmento "sensor" y validamos que termine en "telemetry"
        int sensorIdx = indexOf(parts, "sensor");
        if (sensorIdx == -1) {
            throw new IllegalArgumentException("topic does not contain '/sensor/': " + topic);
        }

        int telemetryIdx = parts.length - 1;
        if (!"telemetry".equals(parts[telemetryIdx])) {
            throw new IllegalArgumentException("topic must end with '/telemetry': " + topic);
        }

        // gatewayUid puede estar o no, depende del formato
        // Si sensorIdx == 3 => agro/farm/zone/sensor/...
        // Si sensorIdx == 4 => agro/farm/zone/{gateway}/sensor/...
        String gatewayUid = (sensorIdx == 4) ? parts[3] : null;

        // Ahora miramos cuántos segmentos hay entre "sensor" y "telemetry"
        // Formato A (simplificado): /sensor/{type}/telemetry  => distancia 2
        // Formato B (v2):           /sensor/{sensorUid}/{type}/telemetry => distancia 3
        int distance = telemetryIdx - sensorIdx;

        if (distance == 2) {
            String sensorType = parts[sensorIdx + 1];
            return new TopicParts(farmCode, zoneCode, gatewayUid, sensorType, null);
        }

        if (distance == 3) {
            String sensorUid = parts[sensorIdx + 1];
            String sensorType = parts[sensorIdx + 2];
            return new TopicParts(farmCode, zoneCode, gatewayUid, sensorType, sensorUid);
        }

        throw new IllegalArgumentException("unsupported sensor topic format: " + topic);
    }

    private static int indexOf(String[] arr, String value) {
        for (int i = 0; i < arr.length; i++) {
            if (value.equals(arr[i])) {
                return i;
            }
        }
        return -1;
    }

    public record TopicParts(
            String farmCode,
            String zoneCode,
            String deviceUidFromTopic, // aquí lo usaremos como gatewayUid si viene en el topic
            String type, // aquí será el sensorType canónico
            String sensorUid // NUEVO: sensorUid (wind-08, ph-05, etc.)
            ) {

        public TopicParts(String farmCode, String zoneCode, String deviceUidFromTopic, String type) {
            this(farmCode, zoneCode, deviceUidFromTopic, type, null);
        }
    }
}
