package com.glea.nexo.domain.inventory;

import java.math.BigDecimal;
import java.time.Instant;

import com.glea.nexo.domain.common.BaseEntityUuid;
import com.glea.nexo.domain.common.enums.OnlineState;
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
@Table(name = "sensor",
        uniqueConstraints = @UniqueConstraint(name = "uk_sensor_org_uid", columnNames = {"organization_id", "sensor_uid"}),
        indexes = {
            @Index(name = "idx_sensor_last_seen", columnList = "last_seen_at"),
            @Index(name = "idx_sensor_zone", columnList = "zone_id"),
            @Index(name = "idx_sensor_device", columnList = "device_id")
        })
public class Sensor extends BaseEntityUuid {

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

    @Column(name = "sensor_uid", length = 80, nullable = false)
    private String sensorUid; // soil_moisture-01

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_type_id", nullable = false)
    private SensorType sensorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit; // override

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private OnlineState state = OnlineState.UNKNOWN;

    private Instant lastSeenAt;
    private Integer lastRssi;

    @Column(precision = 5, scale = 2)
    private BigDecimal lastBatteryV;

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public Farm getFarm() {
        return farm;
    }

    public void setFarm(Farm farm) {
        this.farm = farm;
    }

    public Zone getZone() {
        return zone;
    }

    public void setZone(Zone zone) {
        this.zone = zone;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public String getSensorUid() {
        return sensorUid;
    }

    public void setSensorUid(String sensorUid) {
        this.sensorUid = sensorUid;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public void setSensorType(SensorType sensorType) {
        this.sensorType = sensorType;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public OnlineState getState() {
        return state;
    }

    public void setState(OnlineState state) {
        this.state = state;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Integer getLastRssi() {
        return lastRssi;
    }

    public void setLastRssi(Integer lastRssi) {
        this.lastRssi = lastRssi;
    }

    public BigDecimal getLastBatteryV() {
        return lastBatteryV;
    }

    public void setLastBatteryV(BigDecimal lastBatteryV) {
        this.lastBatteryV = lastBatteryV;
    }
}
