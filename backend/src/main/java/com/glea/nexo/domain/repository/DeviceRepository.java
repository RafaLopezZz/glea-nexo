package com.glea.nexo.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.glea.nexo.domain.common.enums.OnlineState;
import com.glea.nexo.domain.inventory.Device;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByOrganization_IdAndDeviceUid(UUID organizationId, String deviceUid);
    @Modifying
    @Query(value = """
        INSERT INTO device (
          id, created_at, updated_at, device_uid, state,
          organization_id, farm_id, zone_id, name
        )
        VALUES (
          :id, now(), now(), :deviceUid, :state,
          :orgId, :farmId, :zoneId, :name
        )
        ON CONFLICT (organization_id, device_uid) DO NOTHING
        """, nativeQuery = true)
    int insertIgnore(@Param("id") UUID id,
            @Param("deviceUid") String deviceUid,
            @Param("state") String state,
            @Param("orgId") UUID orgId,
            @Param("farmId") UUID farmId,
            @Param("zoneId") UUID zoneId,
            @Param("name") String name);

    Optional<Device> findByIdAndOrganization_Id(UUID deviceId, UUID organizationId);

    boolean existsByOrganization_IdAndDeviceUid(UUID organizationId, String deviceUid);

    @Query("""
            SELECT d FROM Device d
            WHERE d.organization.id = :organizationId
              AND (:farmId IS NULL OR d.farm.id = :farmId)
              AND (:zoneId IS NULL OR d.zone.id = :zoneId)
              AND (:state IS NULL OR d.state = :state)
            """)
    Page<Device> findByOrganizationFiltered(
            @Param("organizationId") UUID organizationId,
            @Param("farmId") UUID farmId,
            @Param("zoneId") UUID zoneId,
            @Param("state") OnlineState state,
            Pageable pageable
    );

    @Query("""
            SELECT d FROM Device d
            WHERE d.organization.id = :organizationId
              AND (:farmId IS NULL OR d.farm.id = :farmId)
              AND (:zoneId IS NULL OR d.zone.id = :zoneId)
              AND (:state IS NULL OR d.state = :state)
              AND (
                    LOWER(d.deviceUid) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(COALESCE(d.name, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Device> searchByOrganization(
            @Param("organizationId") UUID organizationId,
            @Param("farmId") UUID farmId,
            @Param("zoneId") UUID zoneId,
            @Param("state") OnlineState state,
            @Param("q") String q,
            Pageable pageable
    );
}
