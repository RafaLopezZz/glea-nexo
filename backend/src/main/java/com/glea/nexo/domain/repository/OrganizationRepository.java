package com.glea.nexo.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.glea.nexo.domain.location.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByCode(String code);
}
