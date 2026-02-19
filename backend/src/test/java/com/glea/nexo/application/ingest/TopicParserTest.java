package com.glea.nexo.application.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class TopicParserTest {

    private final TopicParser parser = new TopicParser();

    @Test
    void parse_simplified_topic() {
        var p = parser.parse("agro/finca1/zona1/sensor/WIND/telemetry");
        assertEquals("finca1", p.farmCode());
        assertEquals("zona1", p.zoneCode());
        assertNull(p.deviceUidFromTopic());
        assertEquals("WIND", p.type());
        assertNull(p.sensorUid());
    }

    @Test
    void parse_gateway_topic_without_sensorUid() {
        var p = parser.parse("agro/finca1/zona1/pi-gw-001/sensor/WIND/telemetry");
        assertEquals("pi-gw-001", p.deviceUidFromTopic());
        assertEquals("WIND", p.type());
        assertNull(p.sensorUid());
    }

    @Test
    void parse_v2_multisensor_topic() {
        var p = parser.parse("agro/finca1/zona1/pi-gw-001/sensor/wind-08/WIND/telemetry");
        assertEquals("pi-gw-001", p.deviceUidFromTopic());
        assertEquals("wind-08", p.sensorUid());
        assertEquals("WIND", p.type());
    }
}

