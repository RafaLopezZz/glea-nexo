package com.glea.nexo.api.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.glea.nexo.domain.common.enums.IngestStatus;
import com.glea.nexo.domain.common.enums.OnlineState;
import com.glea.nexo.domain.common.enums.QualityLevel;
import com.glea.nexo.domain.ingest.IngestEvent;
import com.glea.nexo.domain.ingest.TelemetryReading;
import com.glea.nexo.domain.inventory.Sensor;
import com.glea.nexo.domain.inventory.SensorType;
import com.glea.nexo.domain.repository.IngestEventRepository;
import com.glea.nexo.domain.repository.SensorRepository;
import com.glea.nexo.domain.repository.SensorTypeRepository;
import com.glea.nexo.domain.repository.TelemetryReadingRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IngestControllerIntegrationTest {

    @Autowired
    private TelemetryReadingRepository telemetryReadingRepository;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.open-in-view", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngestEventRepository ingestEventRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorTypeRepository sensorTypeRepository;

    @BeforeEach
    void ensureSensorCatalog() {
      if (sensorTypeRepository.findByCode("TEMPERATURE").isEmpty()) {
        SensorType sensorType = new SensorType();
        sensorType.setCode("TEMPERATURE");
        sensorType.setName("Temperature");
        sensorTypeRepository.saveAndFlush(sensorType);
      }
    }

    @Test
    void shouldReturnDuplicateWhenSameBatchIsSentTwice() throws Exception {
        String request = """
                {
                  "source": "mqtt-gateway",
                  "topic": "agro/finca-01/zona-01/sensor/temperature/telemetry",
                  "readings": [
                    {
                      "messageId": "m-0001",
                      "deviceId": "temp-01",
                      "ts": "2026-02-01T12:00:00Z",
                      "value": 23.4,
                      "unit": "C",
                      "battery": 3.78,
                      "rssi": -70
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/ingest/readings/batch")
                .contentType("application/json")
                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.errors").value(0))
                .andExpect(jsonPath("$.items[0].status").value("PROCESSED"));

        mockMvc.perform(post("/api/ingest/readings/batch")
                .contentType("application/json")
                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.errors").value(0))
                .andExpect(jsonPath("$.items[0].status").value("DUPLICATE"));

        long eventCount = ingestEventRepository.findAll().stream()
          .filter(event -> "m-0001".equals(event.getMessageId()))
          .count();
        org.junit.jupiter.api.Assertions.assertEquals(1L, eventCount);
    }

    @Test
    @DisplayName("Debe persistir TelemetryReading y actualizar sensor state")
    void shouldPersistTelemetryReadingAndUpdateSensor() throws Exception {
        // Given: payload con lectura válida
        String messageId = "test-telemetry-" + UUID.randomUUID();
        String payload = """
        {
          "source": "integration-test",
          "topic": "agro/finca-test/zona-test/sensor/temperature/telemetry",
          "readings": [
            {
              "messageId": "%s",
              "deviceId": "temp-sensor-01",
              "ts": "2026-02-15T10:00:00Z",
              "value": 23.4,
              "unit": "C",
              "battery": 3.85,
              "rssi": -68
            }
          ]
        }
        """.formatted(messageId);

        // When: POST al endpoint
        mockMvc.perform(post("/api/ingest/readings/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.errors").value(0));

        // Then: Verificar TelemetryReading persistido
        List<TelemetryReading> readings = telemetryReadingRepository
                .findAll()
                .stream()
                .filter(r -> r.getMessageId().equals(messageId))
                .toList();

        assertThat(readings).hasSize(1);
        TelemetryReading reading = readings.get(0);
        assertThat(reading.getValueNum()).isEqualByComparingTo(new BigDecimal("23.4"));
        assertThat(reading.getBatteryV()).isEqualByComparingTo(new BigDecimal("3.85"));
        assertThat(reading.getRssi()).isEqualTo(-68);
        assertThat(reading.getQuality()).isEqualTo(QualityLevel.UNKNOWN);

        // Then: Verificar Sensor actualizado
        Sensor sensor = sensorRepository.findById(reading.getSensor().getId())
          .orElseThrow();
        assertThat(sensor.getState()).isEqualTo(OnlineState.ONLINE);
        assertThat(sensor.getLastSeenAt()).isNotNull();
        assertThat(sensor.getLastBatteryV()).isEqualByComparingTo(new BigDecimal("3.85"));
        assertThat(sensor.getLastRssi()).isEqualTo(-68);

        // Then: Verificar IngestEvent marcado PROCESSED
        IngestEvent event = ingestEventRepository.findById(reading.getIngestEvent().getId())
          .orElseThrow();
        assertThat(event.getStatus()).isEqualTo(IngestStatus.PROCESSED);
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("Debe detectar duplicado de telemetría")
    void shouldDetectDuplicateTelemetry() throws Exception {
        // Given: Mismo mensaje enviado 2 veces
        String messageId = "test-duplicate-" + UUID.randomUUID();
        String payload = """
        {
          "source": "integration-test",
          "topic": "agro/finca-test/zona-test/sensor/temperature/telemetry",
          "readings": [
            {
              "messageId": "%s",
              "deviceId": "temp-sensor-02",
              "ts": "2026-02-15T10:30:00Z",
              "value": 25.0,
              "unit": "C",
              "battery": 3.9,
              "rssi": -65
            }
          ]
        }
        """.formatted(messageId);

        // When: Primera llamada
        mockMvc.perform(post("/api/ingest/readings/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1));

        // When: Segunda llamada (duplicado)
        mockMvc.perform(post("/api/ingest/readings/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.duplicates").value(1));

        // Then: Solo debe existir 1 TelemetryReading
        long count = telemetryReadingRepository
                .findAll()
                .stream()
                .filter(r -> r.getMessageId().equals(messageId))
                .count();

        assertThat(count).isEqualTo(1);
    }

}
