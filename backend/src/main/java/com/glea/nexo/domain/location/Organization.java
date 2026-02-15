package com.glea.nexo.domain.location;

import com.glea.nexo.domain.common.BaseEntityUuid;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "organization",
        uniqueConstraints = @UniqueConstraint(name = "uk_org_code", columnNames = {"code"}))
public class Organization extends BaseEntityUuid {

    @Column(length = 50, nullable = false)
    private String code;

    @Column(length = 120, nullable = false)
    private String name;

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
}
