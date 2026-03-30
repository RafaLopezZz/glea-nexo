package com.glea.nexo.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.glea.nexo.domain.inventory.Device;

@Repository
public interface DeviceAlertRepository extends JpaRepository<Device, UUID> {

    interface DeviceAlertView {
        UUID getZoneId();
        UUID getDeviceId();
        String getDeviceUid();
        Instant getLastSeenAt();
    }

    @Query(value = """
            select
              d.zone_id as zoneId,
              d.id as deviceId,
              d.device_uid as deviceUid,
              d.last_seen_at as lastSeenAt
            from device d
            where d.organization_id = :organizationId
              and d.last_seen_at is not null
              and d.last_seen_at < :staleBefore
              and (:zoneId is null or d.zone_id = :zoneId)
              and (:deviceId is null or d.id = :deviceId)
              and (:fromTs is null or d.last_seen_at >= :fromTs)
              and (:toTs is null or d.last_seen_at <= :toTs)
            order by d.last_seen_at asc, d.id asc
            """, nativeQuery = true)
    List<DeviceAlertView> findStaleDevices(
            @Param("organizationId") UUID organizationId,
            @Param("zoneId") UUID zoneId,
            @Param("deviceId") UUID deviceId,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs,
            @Param("staleBefore") Instant staleBefore
    );
}
