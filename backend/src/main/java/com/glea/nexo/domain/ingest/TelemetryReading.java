package com.glea.nexo.domain.ingest;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.glea.nexo.domain.common.BaseEntityUuid;
import com.glea.nexo.domain.common.enums.QualityLevel;
import com.glea.nexo.domain.inventory.Device;
import com.glea.nexo.domain.inventory.Sensor;
import com.glea.nexo.domain.inventory.Unit;
import com.glea.nexo.domain.location.Farm;
import com.glea.nexo.domain.location.Organization;
import com.glea.nexo.domain.location.Zone;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "telemetry_reading",
        uniqueConstraints = @UniqueConstraint(name = "uk_reading_sensor_message", columnNames = {"sensor_id", "message_id"}),
        indexes = {
            @Index(name = "idx_tr_sensor_ts", columnList = "sensor_id,ts"),
            @Index(name = "idx_tr_zone_ts", columnList = "zone_id,ts"),
            @Index(name = "idx_tr_device_ts", columnList = "device_id,ts")
        })
public class TelemetryReading extends BaseEntityUuid {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id")
    private Farm farm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_id")
    private Sensor sensor;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "value_num", precision = 18, scale = 6)
    private BigDecimal valueNum;

    @Column(length = 120)
    private String valueText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(precision = 5, scale = 2)
    private BigDecimal batteryV;

    private Integer rssi;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private QualityLevel quality = QualityLevel.UNKNOWN;

    @Column(name = "message_id", length = 120, nullable = false)
    private String messageId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ingest_event_id", nullable = false)
    private IngestEvent ingestEvent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode rawPayload;

    // getters/setters (mínimos para empezar)
    public Instant getTs() {
        return ts;
    }

    public void setTs(Instant ts) {
        this.ts = ts;
    }

    public BigDecimal getValueNum() {
        return valueNum;
    }

    public void setValueNum(BigDecimal valueNum) {
        this.valueNum = valueNum;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public IngestEvent getIngestEvent() {
        return ingestEvent;
    }

    public void setIngestEvent(IngestEvent ingestEvent) {
        this.ingestEvent = ingestEvent;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setFarm(Farm farm) {
        this.farm = farm;
    }

    public Farm getFarm() {
        return farm;
    }

    public void setZone(Zone zone) {
        this.zone = zone;
    }

    public Zone getZone() {
        return zone;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public Device getDevice() {
        return device;
    }

    public void setSensor(Sensor sensor) {
        this.sensor = sensor;
    }

    public Sensor getSensor() {
        return sensor;
    }

    public void setBatteryV(BigDecimal batteryV) {
        this.batteryV = batteryV;
    }

    public BigDecimal getBatteryV() {
        return batteryV;
    }

    public void setRssi(Integer rssi) {
        this.rssi = rssi;
    }

    public Integer getRssi() {
        return rssi;
    }

    public void setQuality(QualityLevel quality) {
        this.quality = quality;
    }

    public QualityLevel getQuality() {
        return quality;
    }

    public void setRawPayload(JsonNode rawPayload) {
        this.rawPayload = rawPayload;
    }

    public JsonNode getRawPayload() {
        return rawPayload;
    }

}
