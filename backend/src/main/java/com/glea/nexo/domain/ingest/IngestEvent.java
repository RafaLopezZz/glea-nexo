package com.glea.nexo.domain.ingest;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.glea.nexo.domain.common.BaseEntityUuid;
import com.glea.nexo.domain.common.enums.IngestStatus;
import com.glea.nexo.domain.inventory.Device;
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
@Table(name = "ingest_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_ingest_device_message", columnNames = {"device_id", "message_id"}),
        indexes = {
            @Index(name = "idx_ingest_received", columnList = "received_at"),
            @Index(name = "idx_ingest_status_received", columnList = "status,received_at")
        })
public class IngestEvent extends BaseEntityUuid {

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

    @Column(name = "message_id", length = 120, nullable = false)
    private String messageId;

    @Column(length = 240, nullable = false)
    private String topic;

    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private IngestStatus status = IngestStatus.RECEIVED;

    @Column(length = 80)
    private String errorCode;

    @Column(length = 400)
    private String errorMessage;

    @Column(length = 60)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode rawPayload;

    // getters/setters (mínimos)
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

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public IngestStatus getStatus() {
        return status;
    }

    public void setStatus(IngestStatus status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public JsonNode getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(JsonNode rawPayload) {
        this.rawPayload = rawPayload;
    }
}
