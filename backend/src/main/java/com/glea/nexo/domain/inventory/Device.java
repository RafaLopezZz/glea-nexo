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
@Table(name = "device",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_org_uid", columnNames = {"organization_id", "device_uid"}),
        indexes = {
            @Index(name = "idx_device_last_seen", columnList = "last_seen_at"),
            @Index(name = "idx_device_zone", columnList = "zone_id")
        })
public class Device extends BaseEntityUuid {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id")
    private Farm farm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Column(name = "device_uid", length = 80, nullable = false)
    private String deviceUid; // pi-gw-001

    @Column(length = 120)
    private String name;

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

    public String getDeviceUid() {
        return deviceUid;
    }

    public void setDeviceUid(String deviceUid) {
        this.deviceUid = deviceUid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
