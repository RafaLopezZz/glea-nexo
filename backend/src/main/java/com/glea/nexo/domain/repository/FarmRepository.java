package com.glea.nexo.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.glea.nexo.domain.location.Farm;

@Repository
public interface FarmRepository extends JpaRepository<Farm, UUID> {

    Optional<Farm> findByOrganization_IdAndCode(UUID organizationId, String code);

    Optional<Farm> findByIdAndOrganization_Id(UUID farmId, UUID organizationId);

    boolean existsByOrganization_IdAndCode(UUID organizationId, String code);

    Page<Farm> findByOrganization_Id(UUID organizationId, Pageable pageable);

    @Query("""
            SELECT f FROM Farm f
            WHERE f.organization.id = :organizationId
              AND (
                    LOWER(f.code) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(f.name) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Farm> searchByOrganization(
            @Param("organizationId") UUID organizationId,
            @Param("q") String q,
            Pageable pageable
    );
}
