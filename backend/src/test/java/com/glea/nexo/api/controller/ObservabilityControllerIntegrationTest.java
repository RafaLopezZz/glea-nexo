package com.glea.nexo.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.glea.nexo.domain.inventory.Device;
import com.glea.nexo.domain.inventory.SensorType;
import com.glea.nexo.domain.location.Organization;
import com.glea.nexo.domain.repository.DeviceRepository;
import com.glea.nexo.domain.repository.OrganizationRepository;
import com.glea.nexo.domain.repository.SensorTypeRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ObservabilityControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SensorTypeRepository sensorTypeRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    private Organization defaultOrganization;

    @BeforeEach
    void setup() {
        defaultOrganization = organizationRepository.findByCode("default")
                .orElseGet(() -> {
                    Organization organization = new Organization();
                    organization.setCode("default");
                    organization.setName("Default Organization");
                    return organizationRepository.saveAndFlush(organization);
                });

        ensureSensorType("TEMPERATURE", "Temperature");
    }

    @Test
    void readingsShouldFilterByZoneDeviceAndRange() throws Exception {
        ingest("msg-r-1", "gw-01", "sensor-temp-01", "zone-a", "2026-03-29T10:00:00Z", 21.5);
        ingest("msg-r-2", "gw-01", "sensor-temp-01", "zone-a", "2026-03-29T10:05:00Z", 22.1);
        ingest("msg-r-3", "gw-02", "sensor-temp-02", "zone-b", "2026-03-29T10:06:00Z", 19.8);

        Device zoneADevice = deviceRepository.findByOrganization_IdAndDeviceUid(defaultOrganization.getId(), "gw-01").orElseThrow();

        mockMvc.perform(get("/api/telemetry/readings")
                        .param("zoneId", zoneADevice.getZone().getId().toString())
                        .param("deviceId", zoneADevice.getId().toString())
                        .param("from", "2026-03-29T10:00:00Z")
                        .param("to", "2026-03-29T10:05:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].deviceUid").value("gw-01"))
                .andExpect(jsonPath("$[0].ts").value("2026-03-29T10:00:00Z"))
                .andExpect(jsonPath("$[1].ts").value("2026-03-29T10:05:00Z"));
    }

    @Test
    void latestShouldReturnLastValuePerSensor() throws Exception {
        ingest("msg-l-1", "gw-10", "sensor-temp-10", "zone-latest", "2026-03-29T09:00:00Z", 18.0);
        ingest("msg-l-2", "gw-10", "sensor-temp-10", "zone-latest", "2026-03-29T09:10:00Z", 19.4);

        mockMvc.perform(get("/api/telemetry/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].deviceUid").value("gw-10"))
                .andExpect(jsonPath("$[0].sensorUid").value("sensor-temp-10"))
                .andExpect(jsonPath("$[0].lastTs").value("2026-03-29T09:10:00Z"))
                .andExpect(jsonPath("$[0].value").value(19.4));
    }

    @Test
    void alertsShouldReturnStaleDevices() throws Exception {
        ingest("msg-a-1", "gw-alert", "sensor-temp-alert", "zone-alert", "2026-03-29T08:00:00Z", 17.2);

        Device staleDevice = deviceRepository.findByOrganization_IdAndDeviceUid(defaultOrganization.getId(), "gw-alert")
                .orElseThrow();
        staleDevice.setLastSeenAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        deviceRepository.saveAndFlush(staleDevice);

        mockMvc.perform(get("/api/alerts")
                        .param("deviceId", staleDevice.getId().toString())
                        .param("from", Instant.now().minus(1, ChronoUnit.HOURS).toString())
                        .param("to", Instant.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("DEVICE_STALE"))
                .andExpect(jsonPath("$[0].deviceUid").value("gw-alert"));
    }

    @Test
    void invalidRangeShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/telemetry/readings")
                        .param("from", "2026-03-29T10:10:00Z")
                        .param("to", "2026-03-29T10:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    private void ensureSensorType(String code, String name) {
        if (sensorTypeRepository.findByCode(code).isEmpty()) {
            SensorType sensorType = new SensorType();
            sensorType.setCode(code);
            sensorType.setName(name);
            sensorTypeRepository.saveAndFlush(sensorType);
        }
    }

    private void ingest(String messageId, String deviceUid, String sensorUid, String zoneCode, String ts, double value) throws Exception {
        String payload = """
                {
                  "source": "integration-test",
                  "readings": [
                    {
                      "messageId": "%s",
                      "deviceId": "%s",
                      "topic": "agro/farm-observability/%s/%s/sensor/%s/TEMPERATURE/telemetry",
                      "ts": "%s",
                      "value": %.1f,
                      "unit": "C",
                      "battery": 3.9,
                      "rssi": -67
                    }
                  ]
                }
                """.formatted(messageId, deviceUid, zoneCode, deviceUid, sensorUid, ts, value);

        mockMvc.perform(post("/api/ingest/readings/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }
}
