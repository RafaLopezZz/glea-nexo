package com.glea.nexo.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.glea.nexo.domain.inventory.SensorType;

@Repository
public interface SensorTypeRepository extends JpaRepository<SensorType, Long> {
    
    Optional<SensorType> findByCode(String code);
}

