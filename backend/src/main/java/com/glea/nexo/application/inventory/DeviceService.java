package com.glea.nexo.application.inventory;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.glea.nexo.api.dto.inventory.DeviceCreateRequestDto;
import com.glea.nexo.api.dto.inventory.DeviceResponseDto;
import com.glea.nexo.api.dto.inventory.DeviceUpdateRequestDto;
import com.glea.nexo.domain.common.enums.OnlineState;
import com.glea.nexo.domain.inventory.Device;
import com.glea.nexo.domain.location.Organization;
import com.glea.nexo.domain.location.Zone;
import com.glea.nexo.domain.repository.DeviceRepository;
import com.glea.nexo.domain.repository.ZoneRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final ZoneRepository zoneRepository;
    private final OrganizationContextResolver organizationContextResolver;

    public DeviceService(
            DeviceRepository deviceRepository,
            ZoneRepository zoneRepository,
            OrganizationContextResolver organizationContextResolver
    ) {
        this.deviceRepository = deviceRepository;
        this.zoneRepository = zoneRepository;
        this.organizationContextResolver = organizationContextResolver;
    }

    @Transactional
    public DeviceResponseDto createDevice(UUID zoneId, DeviceCreateRequestDto request) {
        Zone zone = findScopedZone(zoneId);

        Device device = new Device();
        device.setDeviceUid(request.deviceUid().trim());
        device.setName(StringUtils.hasText(request.name()) ? request.name().trim() : null);
        device.setState(OnlineState.UNKNOWN);
        device.setZone(zone);
        device.setFarm(zone.getFarm());
        device.setOrganization(zone.getFarm().getOrganization());

        Device saved = deviceRepository.saveAndFlush(device);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DeviceResponseDto> listDevices(
            int page,
            int size,
            String sort,
            UUID farmId,
            UUID zoneId,
            OnlineState state,
            String q
    ) {
        Organization organization = organizationContextResolver.resolveCurrentOrganization();
        String query = StringUtils.hasText(q) ? q.trim() : null;

        if (!StringUtils.hasText(query)) {
            return deviceRepository.findByOrganizationFiltered(
                    organization.getId(),
                    farmId,
                    zoneId,
                    state,
                    PaginationUtils.buildPageRequest(page, size, sort)
            )
                    .map(this::toResponse);
        }

        return deviceRepository.searchByOrganization(
                organization.getId(),
                farmId,
                zoneId,
                state,
                query,
                PaginationUtils.buildPageRequest(page, size, sort)
        )
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DeviceResponseDto getDevice(UUID deviceId) {
        return toResponse(findScopedDevice(deviceId));
    }

    @Transactional
    public DeviceResponseDto updateDevice(UUID deviceId, DeviceUpdateRequestDto request) {
        Device device = findScopedDevice(deviceId);

        if (StringUtils.hasText(request.name())) {
            device.setName(request.name().trim());
        } else if (request.name() != null) {
            // Si envían name explícitamente como "" o "   ", lo dejamos en null
            device.setName(null);
        }

        if (request.state() != null) {
            device.setState(request.state());
        }

        Device saved = deviceRepository.saveAndFlush(device);
        return toResponse(saved);
    }

    @Transactional
    public void deleteDevice(UUID deviceId) {
        Device device = findScopedDevice(deviceId);
        deviceRepository.delete(device);
        deviceRepository.flush();
    }

    private Zone findScopedZone(UUID zoneId) {
        Organization organization = organizationContextResolver.resolveCurrentOrganization();
        return zoneRepository.findByIdAndFarm_Organization_Id(zoneId, organization.getId())
                .orElseThrow(() -> new EntityNotFoundException("Zone '%s' not found".formatted(zoneId)));
    }

    private Device findScopedDevice(UUID deviceId) {
        Organization organization = organizationContextResolver.resolveCurrentOrganization();
        return deviceRepository.findByIdAndOrganization_Id(deviceId, organization.getId())
                .orElseThrow(() -> new EntityNotFoundException("Device '%s' not found".formatted(deviceId)));
    }

    private DeviceResponseDto toResponse(Device device) {
        return new DeviceResponseDto(
                device.getId(),
                device.getOrganization() != null ? device.getOrganization().getId() : null,
                device.getFarm() != null ? device.getFarm().getId() : null,
                device.getZone() != null ? device.getZone().getId() : null,
                device.getDeviceUid(),
                device.getName(),
                device.getState(),
                device.getLastSeenAt(),
                device.getLastRssi(),
                device.getLastBatteryV(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }
}
