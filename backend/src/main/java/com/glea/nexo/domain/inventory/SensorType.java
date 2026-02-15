package com.glea.nexo.domain.inventory;

import com.glea.nexo.domain.common.BaseEntityLong;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "sensor_type",
        uniqueConstraints = @UniqueConstraint(name = "uk_sensor_type_code", columnNames = {"code"}))
public class SensorType extends BaseEntityLong {

    @Column(length = 50, nullable = false)
    private String code;

    @Column(length = 120, nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_unit_id")
    private Unit defaultUnit;

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

    public Unit getDefaultUnit() {
        return defaultUnit;
    }

    public void setDefaultUnit(Unit defaultUnit) {
        this.defaultUnit = defaultUnit;
    }
}
