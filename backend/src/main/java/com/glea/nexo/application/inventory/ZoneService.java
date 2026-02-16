package com.glea.nexo.application.inventory;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.glea.nexo.api.dto.inventory.ZoneCreateRequestDto;
import com.glea.nexo.api.dto.inventory.ZoneResponseDto;
import com.glea.nexo.api.dto.inventory.ZoneUpdateRequestDto;
import com.glea.nexo.domain.location.Farm;
import com.glea.nexo.domain.location.Organization;
import com.glea.nexo.domain.location.Zone;
import com.glea.nexo.domain.repository.FarmRepository;
import com.glea.nexo.domain.repository.ZoneRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final FarmRepository farmRepository;
    private final OrganizationContextResolver organizationContextResolver;

    public ZoneService(
            ZoneRepository zoneRepository,
            FarmRepository farmRepository,
            OrganizationContextResolver organizationContextResolver
    ) {
        this.zoneRepository = zoneRepository;
        this.farmRepository = farmRepository;
        this.organizationContextResolver = organizationContextResolver;
    }

    @Transactional
    public ZoneResponseDto createZone(UUID farmId, ZoneCreateRequestDto request) {
        Farm farm = findScopedFarm(farmId);

        Zone zone = new Zone();
        zone.setFarm(farm);
        zone.setCode(request.code().trim());
        zone.setName(request.name().trim());
        zone.setGeometry(request.geometry());

        Zone saved = zoneRepository.saveAndFlush(zone);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ZoneResponseDto> listZones(UUID farmId, int page, int size, String sort, String q) {
        Organization organization = organizationContextResolver.resolveCurrentOrganization();
        String query = StringUtils.hasText(q) ? q.trim() : null;

        // Validación temprana de pertenencia de la finca al tenant
        findScopedFarm(farmId);

        if (!StringUtils.hasText(query)) {
            return zoneRepository.findByFarm_IdAndFarm_Organization_Id(
                            farmId,
                            organization.getId(),
                            PaginationUtils.buildPageRequest(page, size, sort)
                    )
                    .map(this::toResponse);
        }

        return zoneRepository.searchByFarmAndOrganization(
                        farmId,
                        organization.getId(),
                        query,
                        PaginationUtils.buildPageRequest(page, size, sort)
                )
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ZoneResponseDto getZone(UUID zoneId) {
        return toResponse(findScopedZone(zoneId));
    }

    @Transactional
    public ZoneResponseDto updateZone(UUID zoneId, ZoneUpdateRequestDto request) {
        Zone zone = findScopedZone(zoneId);
        zone.setCode(request.code().trim());
        zone.setName(request.name().trim());
        zone.setGeometry(request.geometry());

        Zone saved = zoneRepository.saveAndFlush(zone);
        return toResponse(saved);
    }

    @Transactional
    public void deleteZone(UUID zoneId) {
        Zone zone = findScopedZone(zoneId);
        zoneRepository.delete(zone);
        zoneRepository.flush();
    }

    private Farm findScopedFarm(UUID farmId) {
        Organization organization = organizationContextResolver.resolveCurrentOrganization();
        return farmRepository.findByIdAndOrganization_Id(farmId, organization.getId())
                .orElseThrow(() -> new EntityNotFoundException("Farm '%s' not found".formatted(farmId)));
    }

    private Zone findScopedZone(UUID zoneId) {
        Organization organization = organizationContextResolver.resolveCurrentOrganization();
        return zoneRepository.findByIdAndFarm_Organization_Id(zoneId, organization.getId())
                .orElseThrow(() -> new EntityNotFoundException("Zone '%s' not found".formatted(zoneId)));
    }

    private ZoneResponseDto toResponse(Zone zone) {
        return new ZoneResponseDto(
                zone.getId(),
                zone.getFarm().getId(),
                zone.getCode(),
                zone.getName(),
                zone.getGeometry(),
                zone.getCreatedAt(),
                zone.getUpdatedAt()
        );
    }
}
