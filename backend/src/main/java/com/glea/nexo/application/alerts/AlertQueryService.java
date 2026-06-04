package com.glea.nexo.application.alerts;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.glea.nexo.api.dto.alerts.AlertResponseDto;
import com.glea.nexo.application.common.TimeRangeValidator;
import com.glea.nexo.application.inventory.OrganizationContextResolver;
import com.glea.nexo.domain.location.Organization;
import com.glea.nexo.domain.repository.DeviceAlertRepository;

@Service
public class AlertQueryService {

    private static final Duration STALE_WINDOW = Duration.ofMinutes(15);

    private final DeviceAlertRepository deviceAlertRepository;
    private final OrganizationContextResolver organizationContextResolver;
    private final TimeRangeValidator timeRangeValidator;

    public AlertQueryService(
            DeviceAlertRepository deviceAlertRepository,
            OrganizationContextResolver organizationContextResolver,
            TimeRangeValidator timeRangeValidator
    ) {
        this.deviceAlertRepository = deviceAlertRepository;
        this.organizationContextResolver = organizationContextResolver;
        this.timeRangeValidator = timeRangeValidator;
    }

    public List<AlertResponseDto> findAlerts(UUID zoneId, UUID deviceId, Instant from, Instant to) {
        validateRange(from, to);
        Organization organization = organizationContextResolver.resolveCurrentOrganization();
        Instant staleBefore = Instant.now().minus(STALE_WINDOW);

        return deviceAlertRepository.findStaleDevices(organization.getId(), zoneId, deviceId, from, to, staleBefore)
                .stream()
                .map(view -> new AlertResponseDto(
                        "DEVICE_STALE",
                        "WARN",
                        view.getLastSeenAt(),
                        view.getZoneId(),
                        view.getDeviceId(),
                        view.getDeviceUid(),
                        null,
                        null,
                        "Device %s sin telemetría reciente desde %s".formatted(view.getDeviceUid(), view.getLastSeenAt())))
                .toList();
    }

    private void validateRange(Instant from, Instant to) {
        timeRangeValidator.validate(from, to);
    }
}
