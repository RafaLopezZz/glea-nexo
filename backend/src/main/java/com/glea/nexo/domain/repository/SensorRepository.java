package com.glea.nexo.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.glea.nexo.domain.inventory.Sensor;

@Repository
public interface SensorRepository extends JpaRepository<Sensor, UUID> {
    
    Optional<Sensor> findByOrganization_IdAndSensorUid(UUID organizationId, String sensorUid);
    
    boolean existsByOrganization_IdAndSensorUid(UUID organizationId, String sensorUid);
}
