package com.glea.nexo.application.telemetry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.glea.nexo.api.dto.telemetry.TelemetryLatestResponseDto;
import com.glea.nexo.api.dto.telemetry.TelemetryReadingResponseDto;
import com.glea.nexo.application.inventory.OrganizationContextResolver;
import com.glea.nexo.domain.location.Organization;
import com.glea.nexo.domain.repository.TelemetryQueryRepository;

@Service
public class TelemetryQueryService {

    private final TelemetryQueryRepository telemetryQueryRepository;
    private final OrganizationContextResolver organizationContextResolver;

    public TelemetryQueryService(
            TelemetryQueryRepository telemetryQueryRepository,
            OrganizationContextResolver organizationContextResolver
    ) {
        this.telemetryQueryRepository = telemetryQueryRepository;
        this.organizationContextResolver = organizationContextResolver;
    }

    public List<TelemetryReadingResponseDto> findReadings(UUID zoneId, UUID deviceId, Instant from, Instant to) {
        validateRange(from, to);
        Organization organization = organizationContextResolver.resolveCurrentOrganization();
        return telemetryQueryRepository.findReadings(organization.getId(), zoneId, deviceId, from, to)
                .stream()
                .map(view -> new TelemetryReadingResponseDto(
                        view.getReadingId(),
                        view.getTs(),
                        view.getZoneId(),
                        view.getDeviceId(),
                        view.getDeviceUid(),
                        view.getSensorId(),
                        view.getSensorUid(),
                        view.getSensorType(),
                        view.getValue(),
                        view.getUnit(),
                        view.getQuality(),
                        view.getBattery(),
                        view.getRssi()))
                .toList();
    }

    public List<TelemetryLatestResponseDto> findLatest(UUID zoneId, UUID deviceId, Instant from, Instant to) {
        validateRange(from, to);
        Organization organization = organizationContextResolver.resolveCurrentOrganization();
        return telemetryQueryRepository.findLatest(organization.getId(), zoneId, deviceId, from, to)
                .stream()
                .map(view -> new TelemetryLatestResponseDto(
                        view.getZoneId(),
                        view.getDeviceId(),
                        view.getDeviceUid(),
                        view.getDeviceState(),
                        view.getDeviceBattery(),
                        view.getDeviceRssi(),
                        view.getSensorId(),
                        view.getSensorUid(),
                        view.getSensorType(),
                        view.getLastTs(),
                        view.getValue(),
                        view.getUnit(),
                        view.getQuality()))
                .toList();
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
    }
}
