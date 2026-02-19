package com.glea.nexo.application.ingest;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glea.nexo.api.dto.ingest.IngestBatchRequestDto;
import com.glea.nexo.api.dto.ingest.IngestReadingDto;
import com.glea.nexo.domain.common.enums.IngestStatus;
import com.glea.nexo.domain.common.enums.OnlineState;
import com.glea.nexo.domain.common.enums.QualityLevel;
import com.glea.nexo.domain.ingest.IngestEvent;
import com.glea.nexo.domain.ingest.TelemetryReading;
import com.glea.nexo.domain.inventory.Device;
import com.glea.nexo.domain.inventory.Sensor;
import com.glea.nexo.domain.inventory.SensorType;
import com.glea.nexo.domain.inventory.Unit;
import com.glea.nexo.domain.location.Farm;
import com.glea.nexo.domain.location.Organization;
import com.glea.nexo.domain.location.Zone;
import com.glea.nexo.domain.repository.DeviceRepository;
import com.glea.nexo.domain.repository.FarmRepository;
import com.glea.nexo.domain.repository.IngestEventRepository;
import com.glea.nexo.domain.repository.OrganizationRepository;
import com.glea.nexo.domain.repository.SensorRepository;
import com.glea.nexo.domain.repository.SensorTypeRepository;
import com.glea.nexo.domain.repository.TelemetryReadingRepository;
import com.glea.nexo.domain.repository.UnitRepository;
import com.glea.nexo.domain.repository.ZoneRepository;

@Component
public class IngestItemProcessor {

    private static final Logger log = LoggerFactory.getLogger(IngestItemProcessor.class);
    private static final String DEFAULT_ORG_CODE = "default";

    private final TopicParser topicParser;
    private final OrganizationRepository organizationRepository;
    private final FarmRepository farmRepository;
    private final ZoneRepository zoneRepository;
    private final DeviceRepository deviceRepository;
    private final IngestEventRepository ingestEventRepository;
    private final SensorRepository sensorRepository;
    private final SensorTypeRepository sensorTypeRepository;
    private final UnitRepository unitRepository;
    private final TelemetryReadingRepository telemetryReadingRepository;
    private final ObjectMapper objectMapper;

    public IngestItemProcessor(
            TopicParser topicParser,
            OrganizationRepository organizationRepository,
            FarmRepository farmRepository,
            ZoneRepository zoneRepository,
            DeviceRepository deviceRepository,
            IngestEventRepository ingestEventRepository,
            SensorRepository sensorRepository,
            SensorTypeRepository sensorTypeRepository,
            UnitRepository unitRepository,
            TelemetryReadingRepository telemetryReadingRepository,
            ObjectMapper objectMapper) {
        this.topicParser = topicParser;
        this.organizationRepository = organizationRepository;
        this.farmRepository = farmRepository;
        this.zoneRepository = zoneRepository;
        this.deviceRepository = deviceRepository;
        this.ingestEventRepository = ingestEventRepository;
        this.sensorRepository = sensorRepository;
        this.sensorTypeRepository = sensorTypeRepository;
        this.unitRepository = unitRepository;
        this.telemetryReadingRepository = telemetryReadingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IngestItemResult process(IngestBatchRequestDto request, IngestReadingDto reading, int index) {
        String resolvedTopic = resolveTopic(request, reading);
        TopicParser.TopicParts topicParts = topicParser.parse(resolvedTopic);
        // String deviceUid = resolveDeviceUid(reading, topicParts);
        String gatewayUid = resolveGatewayUid(reading, topicParts);

        Organization organization = resolveOrganization();
        Farm farm = resolveFarm(organization, topicParts.farmCode());
        Zone zone = resolveZone(farm, topicParts.zoneCode());
        Device device = resolveDevice(organization, farm, zone, gatewayUid);

        if (ingestEventRepository.existsByDevice_IdAndMessageId(device.getId(), reading.messageId())) {
            log.info("ingest duplicate pre-check messageId={} gatewayUid={}", reading.messageId(), gatewayUid);
            return IngestItemResult.duplicate(index, reading.messageId(), "duplicate ingest event by exists check");
        }

        IngestEvent ingestEvent = new IngestEvent();
        ingestEvent.setOrganization(organization);
        ingestEvent.setFarm(farm);
        ingestEvent.setZone(zone);
        ingestEvent.setDevice(device);
        ingestEvent.setMessageId(reading.messageId());
        ingestEvent.setTopic(resolvedTopic);
        ingestEvent.setSource(request.source());
        ingestEvent.setReceivedAt(Instant.now());
        ingestEvent.setStatus(IngestStatus.RECEIVED); // ← Estado inicial
        ingestEvent.setRawPayload(
                reading.rawPayload() != null
                ? reading.rawPayload()
                : objectMapper.valueToTree(reading)
        );

        try {
            ingestEventRepository.saveAndFlush(ingestEvent);
            // ═══════════════════════════════════════════════════════════
            // FASE 3: Resolver tipo de sensor desde topic
            // ═══════════════════════════════════════════════════════════
            String sensorTypeCode = extractSensorTypeFromTopic(topicParts);
            SensorType sensorType = resolveSensorType(sensorTypeCode);

            // ═══════════════════════════════════════════════════════════
            // FASE 4: Resolver unidad (si viene en reading)
            // ═══════════════════════════════════════════════════════════
            Unit unit = null;
            if (reading.unit() != null && !reading.unit().isBlank()) {
                unit = resolveUnit(reading.unit());
            }

            // ═══════════════════════════════════════════════════════════
            // FASE 5: Resolver/crear sensor (multisensor: gatewayUid + sensorUid)
            // ═══════════════════════════════════════════════════════════
            String sensorUid = resolveSensorUid(reading, topicParts);
            Sensor sensor = resolveSensor(
                    organization, farm, zone, device,
                    sensorType, unit, sensorUid
            );

            // ═══════════════════════════════════════════════════════════
            // FASE 6: Check duplicado a nivel telemetría
            // ═══════════════════════════════════════════════════════════
            if (telemetryReadingRepository.existsBySensor_IdAndMessageId(
                    sensor.getId(), reading.messageId())) {
                log.info("Telemetry duplicate pre-check: messageId={}, sensorUid={}",
                        reading.messageId(), sensorUid);
                return IngestItemResult.duplicate(index, reading.messageId(),
                        "duplicate telemetry reading by exists check");
            }

            // ═══════════════════════════════════════════════════════════
            // FASE 7: Persistir TelemetryReading
            // ═══════════════════════════════════════════════════════════
            TelemetryReading telemetry = new TelemetryReading();
            telemetry.setIngestEvent(ingestEvent);
            telemetry.setOrganization(organization);
            telemetry.setFarm(farm);
            telemetry.setZone(zone);
            telemetry.setDevice(device);
            telemetry.setSensor(sensor);
            telemetry.setUnit(unit);

            telemetry.setMessageId(reading.messageId());
            telemetry.setTs(reading.ts() != null ? reading.ts() : Instant.now());
            telemetry.setValueNum(reading.value());
            telemetry.setBatteryV(reading.battery());
            telemetry.setRssi(reading.rssi());
            telemetry.setQuality(QualityLevel.UNKNOWN); // DTO no trae quality
            telemetry.setRawPayload(ingestEvent.getRawPayload());

            telemetry = telemetryReadingRepository.saveAndFlush(telemetry);

            // ═══════════════════════════════════════════════════════════
            // FASE 8: Actualizar estado sensor (desnormalización)
            // ═══════════════════════════════════════════════════════════
            sensor.setLastSeenAt(telemetry.getTs());
            sensor.setLastBatteryV(telemetry.getBatteryV());
            sensor.setLastRssi(telemetry.getRssi());
            sensor.setState(OnlineState.ONLINE);
            sensorRepository.save(sensor);

            // ═══════════════════════════════════════════════════════════
            // FASE 9: Marcar IngestEvent como procesado
            // ═══════════════════════════════════════════════════════════
            ingestEvent.setStatus(IngestStatus.PROCESSED);
            ingestEvent.setProcessedAt(Instant.now());
            ingestEventRepository.save(ingestEvent);

            log.info("Telemetry persisted: messageId={}, gatewayUid={}, sensorUid={}, value={}",
                    reading.messageId(), gatewayUid, sensorUid, reading.value());

            return IngestItemResult.processed(index, reading.messageId(),
                    "telemetry reading persisted");

        } catch (DataIntegrityViolationException ex) {
            // Constraint UNIQUE saltó (race condition)
            log.info("Duplicate by unique constraint: messageId={}, gatewayUid={}, value={}. Cause={}",
                    reading.messageId(), gatewayUid, reading.value(), ex.getMostSpecificCause().getMessage());

            return IngestItemResult.duplicate(index, reading.messageId(),
                    "duplicate by unique constraint");

        } catch (Exception ex) {
            // Error inesperado: marcar ingest_event como ERROR
            log.error("Error processing item: messageId={}, error={}",
                    reading.messageId(), ex.getMessage(), ex);

            try {
                ingestEvent.setStatus(IngestStatus.ERROR);
                ingestEvent.setErrorCode(ex.getClass().getSimpleName());
                ingestEvent.setErrorMessage(
                        ex.getMessage() != null
                        ? ex.getMessage().substring(0, Math.min(400, ex.getMessage().length()))
                        : "Unknown error"
                );
                ingestEventRepository.save(ingestEvent);
            } catch (Exception ignored) {
                // Si falla guardar el error, no podemos hacer nada
            }

            return IngestItemResult.error(index, reading.messageId(),
                    "error persisting telemetry: " + ex.getMessage());
        }
    }

    private String resolveTopic(IngestBatchRequestDto request, IngestReadingDto reading) {
        if (StringUtils.hasText(reading.topic())) {
            return reading.topic();
        }
        if (StringUtils.hasText(request.topic())) {
            return request.topic();
        }
        throw new IllegalArgumentException("topic is missing in reading and batch");
    }

    private String resolveGatewayUid(IngestReadingDto reading, TopicParser.TopicParts topicParts) {
        // Preferimos lo que viene en el topic (v2)
        if (StringUtils.hasText(topicParts.deviceUidFromTopic())) {
            return topicParts.deviceUidFromTopic().trim();
        }

        // Fallback: payload deviceId
        if (StringUtils.hasText(reading.deviceId())) {
            String raw = reading.deviceId().trim();
            int idx = raw.indexOf(':');
            return (idx > 0) ? raw.substring(0, idx) : raw;
        }

        throw new IllegalArgumentException("gatewayUid/deviceId is required (topic or payload)");
    }

    private String resolveSensorUid(IngestReadingDto reading, TopicParser.TopicParts topicParts) {
        if (StringUtils.hasText(topicParts.sensorUid())) {
            return topicParts.sensorUid().trim(); // ✅ v2
        }

        String raw = reading.deviceId().trim();
        int idx = raw.indexOf(':');
        if (idx > 0 && idx < raw.length() - 1) {
            return raw.substring(idx + 1); // fallback legacy gw:sensor
        }

        throw new IllegalArgumentException("sensorUid is required in topic v2 (or deviceId as gw:sensor)");

    }

    private Organization resolveOrganization() {
        return organizationRepository.findByCode(DEFAULT_ORG_CODE)
                .orElseGet(() -> createOrganizationWithRetry());
    }

    private Organization createOrganizationWithRetry() {
        Organization organization = new Organization();
        organization.setCode(DEFAULT_ORG_CODE);
        organization.setName("Default Organization");
        try {
            return organizationRepository.saveAndFlush(organization);
        } catch (DataIntegrityViolationException ex) {
            return organizationRepository.findByCode(DEFAULT_ORG_CODE)
                    .orElseThrow(() -> ex);
        }
    }

    private Farm resolveFarm(Organization organization, String farmCode) {
        return farmRepository.findByOrganization_IdAndCode(organization.getId(), farmCode)
                .orElseGet(() -> createFarmWithRetry(organization, farmCode));
    }

    private Farm createFarmWithRetry(Organization organization, String farmCode) {
        Farm farm = new Farm();
        farm.setOrganization(organization);
        farm.setCode(farmCode);
        farm.setName(farmCode);
        try {
            return farmRepository.saveAndFlush(farm);
        } catch (DataIntegrityViolationException ex) {
            return farmRepository.findByOrganization_IdAndCode(organization.getId(), farmCode)
                    .orElseThrow(() -> ex);
        }
    }

    private Zone resolveZone(Farm farm, String zoneCode) {
        return zoneRepository.findByFarm_IdAndCode(farm.getId(), zoneCode)
                .orElseGet(() -> createZoneWithRetry(farm, zoneCode));
    }

    private Zone createZoneWithRetry(Farm farm, String zoneCode) {
        Zone zone = new Zone();
        zone.setFarm(farm);
        zone.setCode(zoneCode);
        zone.setName(zoneCode);
        try {
            return zoneRepository.saveAndFlush(zone);
        } catch (DataIntegrityViolationException ex) {
            return zoneRepository.findByFarm_IdAndCode(farm.getId(), zoneCode)
                    .orElseThrow(() -> ex);
        }
    }

    private Device resolveDevice(Organization org, Farm farm, Zone zone, String gatewayUid) {

        return deviceRepository.findByOrganization_IdAndDeviceUid(org.getId(), gatewayUid)
                .orElseGet(() -> {
                    deviceRepository.insertIgnore(
                            UUID.randomUUID(),
                            gatewayUid,
                            OnlineState.UNKNOWN.name(),
                            org.getId(),
                            farm.getId(),
                            zone.getId(),
                            null
                    );

                    return deviceRepository.findByOrganization_IdAndDeviceUid(org.getId(), gatewayUid)
                            .orElseThrow(() -> new IllegalStateException("Device upsert failed for gatewayUid=" + gatewayUid));
                });
    }

    private Device createDeviceWithRetry(Organization organization, Farm farm, Zone zone, String deviceUid) {
        Device device = new Device();
        device.setOrganization(organization);
        device.setFarm(farm);
        device.setZone(zone);
        device.setDeviceUid(deviceUid);
        device.setName(deviceUid);
        try {
            return deviceRepository.saveAndFlush(device);
        } catch (DataIntegrityViolationException ex) {
            return deviceRepository.findByOrganization_IdAndDeviceUid(organization.getId(), deviceUid)
                    .orElseThrow(() -> ex);
        }
    }

    public record IngestItemResult(
            int index,
            String messageId,
            ItemStatus status,
            String detail) {

        public static IngestItemResult processed(int index, String messageId, String detail) {
            return new IngestItemResult(index, messageId, ItemStatus.PROCESSED, detail);
        }

        public static IngestItemResult duplicate(int index, String messageId, String detail) {
            return new IngestItemResult(index, messageId, ItemStatus.DUPLICATE, detail);
        }

        public static IngestItemResult error(int index, String messageId, String detail) {
            return new IngestItemResult(index, messageId, ItemStatus.ERROR, detail);
        }
    }

    // ═══════════════════════════════════════════════════════════════
// MÉTODOS HELPER NUEVOS
// ═══════════════════════════════════════════════════════════════
    /**
     * Extrae el tipo de sensor del topic. Ejemplo:
     * agro/finca-01/zona-a/sensor/TEMPERATURA/telemetry → "TEMPERATURE"
     */
    private String extractSensorTypeFromTopic(TopicParser.TopicParts topicParts) {
        String type = topicParts.type(); // Obtiene parte "temperatura" del topic

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Cannot extract sensor type from topic");
        }

        // Normalizar a código: temperatura → TEMPERATURE
        return type.toUpperCase().replace(' ', '_');
    }

    /**
     * Resuelve o crea SensorType. IMPORTANTE: Validación estricta contra
     * catálogo pre-poblado.
     */
    private SensorType resolveSensorType(String code) {
        return sensorTypeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException(
                "Sensor type '" + code + "' not found. "
                + "Valid types: TEMPERATURE, SOIL_MOISTURE, HUMIDITY, PH, EC, LIGHT, PRESSURE, GPS"
        ));
    }

    /**
     * Resuelve o crea Unit. Si no existe, crea uno nuevo (auto-provisioning).
     */
    private Unit resolveUnit(String code) {
        String normalizedCode = code.toUpperCase().replace(' ', '_');

        return unitRepository.findByCode(normalizedCode)
                .orElseGet(() -> createUnitWithRetry(normalizedCode, code));
    }

    private Unit createUnitWithRetry(String code, String symbol) {
        Unit unit = new Unit();
        unit.setCode(code);
        unit.setSymbol(symbol);
        unit.setName(symbol); // Fallback: name = symbol si no sabemos mejor

        try {
            return unitRepository.saveAndFlush(unit);
        } catch (DataIntegrityViolationException ex) {
            return unitRepository.findByCode(code)
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Resuelve o crea Sensor. CLAVE: sensorUid es único por organización
     * (UNIQUE constraint).
     */
    private Sensor resolveSensor(
            Organization organization,
            Farm farm,
            Zone zone,
            Device device,
            SensorType sensorType,
            Unit unit,
            String sensorUid
    ) {
        return sensorRepository
                .findByOrganization_IdAndSensorUid(organization.getId(), sensorUid)
                .orElseGet(() -> createSensorWithRetry(
                organization, farm, zone, device,
                sensorType, unit, sensorUid
        ));
    }

    private Sensor createSensorWithRetry(
            Organization organization,
            Farm farm,
            Zone zone,
            Device device,
            SensorType sensorType,
            Unit unit,
            String sensorUid
    ) {
        Sensor sensor = new Sensor();
        sensor.setOrganization(organization);
        sensor.setFarm(farm);
        sensor.setZone(zone);
        sensor.setDevice(device);
        sensor.setSensorUid(sensorUid);
        sensor.setSensorType(sensorType);
        sensor.setUnit(unit);
        sensor.setState(OnlineState.UNKNOWN); // Estado inicial

        try {
            return sensorRepository.saveAndFlush(sensor);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: otro hilo creó el sensor
            return sensorRepository
                    .findByOrganization_IdAndSensorUid(organization.getId(), sensorUid)
                    .orElseThrow(() -> ex);
        }
    }

    public enum ItemStatus {
        PROCESSED,
        DUPLICATE,
        ERROR
    }
}
