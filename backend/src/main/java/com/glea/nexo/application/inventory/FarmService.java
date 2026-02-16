package com.glea.nexo.application.inventory;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.glea.nexo.api.dto.inventory.FarmCreateRequestDto;
import com.glea.nexo.api.dto.inventory.FarmResponseDto;
import com.glea.nexo.api.dto.inventory.FarmUpdateRequestDto;
import com.glea.nexo.domain.location.Farm;
import com.glea.nexo.domain.location.Organization;
import com.glea.nexo.domain.repository.FarmRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class FarmService {

    private final FarmRepository farmRepository;
    private final OrganizationContextResolver organizationContextResolver;

    public FarmService(FarmRepository farmRepository, OrganizationContextResolver organizationContextResolver) {
        this.farmRepository = farmRepository;
        this.organizationContextResolver = organizationContextResolver;
    }

    @Transactional
    public FarmResponseDto createFarm(FarmCreateRequestDto request) {
        Organization organization = organizationContextResolver.resolveCurrentOrganization();

        Farm farm = new Farm();
        farm.setOrganization(organization);
        farm.setCode(request.code().trim());
        farm.setName(request.name().trim());
        farm.setLocation(request.location());

        Farm saved = farmRepository.saveAndFlush(farm);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<FarmResponseDto> listFarms(int page, int size, String sort, String q) {
        Organization organization = organizationContextResolver.resolveCurrentOrganization();
        String query = StringUtils.hasText(q) ? q.trim() : null;

        if (!StringUtils.hasText(query)) {
            return farmRepository.findByOrganization_Id(
                            organization.getId(),
                            PaginationUtils.buildPageRequest(page, size, sort)
                    )
                    .map(this::toResponse);
        }

        return farmRepository.searchByOrganization(
                        organization.getId(),
                        query,
                        PaginationUtils.buildPageRequest(page, size, sort)
                )
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FarmResponseDto getFarm(UUID farmId) {
        return toResponse(findScopedFarm(farmId));
    }

    @Transactional
    public FarmResponseDto updateFarm(UUID farmId, FarmUpdateRequestDto request) {
        Farm farm = findScopedFarm(farmId);
        farm.setCode(request.code().trim());
        farm.setName(request.name().trim());
        farm.setLocation(request.location());
        Farm updated = farmRepository.saveAndFlush(farm);
        return toResponse(updated);
    }

    @Transactional
    public void deleteFarm(UUID farmId) {
        Farm farm = findScopedFarm(farmId);
        farmRepository.delete(farm);
        farmRepository.flush();
    }

    private Farm findScopedFarm(UUID farmId) {
        Organization organization = organizationContextResolver.resolveCurrentOrganization();
        return farmRepository.findByIdAndOrganization_Id(farmId, organization.getId())
                .orElseThrow(() -> new EntityNotFoundException("Farm '%s' not found".formatted(farmId)));
    }

    private FarmResponseDto toResponse(Farm farm) {
        UUID organizationId = farm.getOrganization() != null ? farm.getOrganization().getId() : null;
        return new FarmResponseDto(
                farm.getId(),
                organizationId,
                farm.getCode(),
                farm.getName(),
                farm.getLocation(),
                farm.getCreatedAt(),
                farm.getUpdatedAt()
        );
    }
}
