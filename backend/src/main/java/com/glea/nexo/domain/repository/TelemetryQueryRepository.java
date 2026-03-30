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

    interface TelemetryReadingView {
        UUID getReadingId();
        Instant getTs();
        UUID getZoneId();
        UUID getDeviceId();
        String getDeviceUid();
        UUID getSensorId();
        String getSensorUid();
        String getSensorType();
        java.math.BigDecimal getValue();
        String getUnit();
        String getQuality();
        java.math.BigDecimal getBattery();
        Integer getRssi();
    }

    interface TelemetryLatestView {
        UUID getZoneId();
        UUID getDeviceId();
        String getDeviceUid();
        String getDeviceState();
        java.math.BigDecimal getDeviceBattery();
        Integer getDeviceRssi();
        UUID getSensorId();
        String getSensorUid();
        String getSensorType();
        Instant getLastTs();
        java.math.BigDecimal getValue();
        String getUnit();
        String getQuality();
    }

    @Query(value = """
            select
              tr.id as readingId,
              tr.ts as ts,
              tr.zone_id as zoneId,
              tr.device_id as deviceId,
              d.device_uid as deviceUid,
              tr.sensor_id as sensorId,
              s.sensor_uid as sensorUid,
              st.code as sensorType,
              tr.value_num as value,
              u.code as unit,
              tr.quality as quality,
              tr.battery_v as battery,
              tr.rssi as rssi
            from telemetry_reading tr
            join device d on d.id = tr.device_id
            join sensor s on s.id = tr.sensor_id
            join sensor_type st on st.id = s.sensor_type_id
            left join unit u on u.id = coalesce(tr.unit_id, s.unit_id, st.default_unit_id)
            where tr.organization_id = :organizationId
              and (:zoneId is null or tr.zone_id = :zoneId)
              and (:deviceId is null or tr.device_id = :deviceId)
              and (:fromTs is null or tr.ts >= :fromTs)
              and (:toTs is null or tr.ts <= :toTs)
            order by tr.ts asc, tr.id asc
            """, nativeQuery = true)
    List<TelemetryReadingView> findReadings(
            @Param("organizationId") UUID organizationId,
            @Param("zoneId") UUID zoneId,
            @Param("deviceId") UUID deviceId,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs
    );

    @Query(value = """
            select * from (
              select
                tr.zone_id as zoneId,
                tr.device_id as deviceId,
                d.device_uid as deviceUid,
                d.state as deviceState,
                d.last_battery_v as deviceBattery,
                d.last_rssi as deviceRssi,
                tr.sensor_id as sensorId,
                s.sensor_uid as sensorUid,
                st.code as sensorType,
                tr.ts as lastTs,
                tr.value_num as value,
                u.code as unit,
                tr.quality as quality,
                row_number() over (partition by tr.sensor_id order by tr.ts desc, tr.id desc) as rn
              from telemetry_reading tr
              join device d on d.id = tr.device_id
              join sensor s on s.id = tr.sensor_id
              join sensor_type st on st.id = s.sensor_type_id
              left join unit u on u.id = coalesce(tr.unit_id, s.unit_id, st.default_unit_id)
              where tr.organization_id = :organizationId
                and (:zoneId is null or tr.zone_id = :zoneId)
                and (:deviceId is null or tr.device_id = :deviceId)
                and (:fromTs is null or tr.ts >= :fromTs)
                and (:toTs is null or tr.ts <= :toTs)
            ) q
            where q.rn = 1
            order by q.lastTs desc, q.sensorId asc
            """, nativeQuery = true)
    List<TelemetryLatestView> findLatest(
            @Param("organizationId") UUID organizationId,
            @Param("zoneId") UUID zoneId,
            @Param("deviceId") UUID deviceId,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs
    );
}
