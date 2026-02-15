package com.glea.nexo.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.glea.nexo.domain.ingest.TelemetryReading;

public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, UUID> {

    List<TelemetryReading> findTop200ByZone_IdOrderByTsDesc(UUID zoneId);

    List<TelemetryReading> findBySensor_IdAndTsBetweenOrderByTsAsc(UUID sensorId, Instant from, Instant to);

    boolean existsBySensor_IdAndMessageId(UUID sensorId, String messageId);
}
