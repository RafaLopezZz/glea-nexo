package com.glea.nexo.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.glea.nexo.domain.location.Farm;

public interface FarmRepository extends JpaRepository<Farm, UUID> {

    Optional<Farm> findByOrganization_IdAndCode(UUID organizationId, String code);
}
