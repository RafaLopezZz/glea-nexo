package com.glea.nexo.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.glea.nexo.domain.location.Zone;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    Optional<Zone> findByFarm_IdAndCode(UUID farmId, String code);
}
