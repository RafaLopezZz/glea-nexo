package com.glea.nexo.domain.location;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.glea.nexo.domain.common.BaseEntityUuid;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "zone",
        uniqueConstraints = @UniqueConstraint(name = "uk_zone_farm_code", columnNames = {"farm_id", "code"}))
public class Zone extends BaseEntityUuid {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @Column(length = 50, nullable = false)
    private String code;

    @Column(length = 120, nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode geometry; // opcional

    public Farm getFarm() {
        return farm;
    }

    public void setFarm(Farm farm) {
        this.farm = farm;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JsonNode getGeometry() {
        return geometry;
    }

    public void setGeometry(JsonNode geometry) {
        this.geometry = geometry;
    }
}
