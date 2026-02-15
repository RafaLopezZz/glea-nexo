package com.glea.nexo.domain.inventory;

import com.glea.nexo.domain.common.BaseEntityLong;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "unit",
        uniqueConstraints = @UniqueConstraint(name = "uk_unit_code", columnNames = {"code"}))
public class Unit extends BaseEntityLong {

    @Column(length = 20, nullable = false)
    private String code;

    @Column(length = 60, nullable = false)
    private String name;

    @Column(length = 10)
    private String symbol; // ej: "°C", "%", "pH"

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

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}
