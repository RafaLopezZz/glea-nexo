package com.glea.nexo.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.glea.nexo.domain.ingest.TelemetryReading;

@Repository
public interface TelemetryQueryRepository extends JpaRepository<TelemetryReading, UUID> {

    @Query(value = """
            select * from vw_telemetry_readings
            where organization_id = :organizationId
              and (:zoneId is null or zone_id = :zoneId)
              and (:deviceId is null or device_id = :deviceId)
              and (:fromTs is null or ts >= :fromTs)
              and (:toTs is null or ts <= :toTs)
            order by ts asc
            """, nativeQuery = true)
    List<Object[]> findReadingsRaw(
            @Param("organizationId") UUID organizationId,
            @Param("zoneId") UUID zoneId,
            @Param("deviceId") UUID deviceId,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs
    );

    @Query(value = """
            select * from vw_telemetry_latest
            where organization_id = :organizationId
              and (:zoneId is null or zone_id = :zoneId)
              and (:deviceId is null or device_id = :deviceId)
              and (:fromTs is null or last_ts >= :fromTs)
              and (:toTs is null or last_ts <= :toTs)
            order by last_ts desc, sensor_id asc
            """, nativeQuery = true)
    List<Object[]> findLatestRaw(
            @Param("organizationId") UUID organizationId,
            @Param("zoneId") UUID zoneId,
            @Param("deviceId") UUID deviceId,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs
    );
}
