package com.glea.nexo.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.glea.nexo.domain.location.Zone;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    Optional<Zone> findByFarm_IdAndCode(UUID farmId, String code);

    Optional<Zone> findByIdAndFarm_Organization_Id(UUID zoneId, UUID organizationId);

    boolean existsByFarm_IdAndCode(UUID farmId, String code);

    Page<Zone> findByFarm_IdAndFarm_Organization_Id(UUID farmId, UUID organizationId, Pageable pageable);

    @Query("""
            SELECT z FROM Zone z
            WHERE z.farm.id = :farmId
              AND z.farm.organization.id = :organizationId
              AND (
                    LOWER(z.code) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(z.name) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Zone> searchByFarmAndOrganization(
            @Param("farmId") UUID farmId,
            @Param("organizationId") UUID organizationId,
            @Param("q") String q,
            Pageable pageable
    );
}
