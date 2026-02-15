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
@Table(name = "farm",
        uniqueConstraints = @UniqueConstraint(name = "uk_farm_org_code", columnNames = {"organization_id", "code"}))
public class Farm extends BaseEntityUuid {

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(length = 50, nullable = false)
    private String code;

    @Column(length = 120, nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode location; // opcional

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
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

    public JsonNode getLocation() {
        return location;
    }

    public void setLocation(JsonNode location) {
        this.location = location;
    }
}
