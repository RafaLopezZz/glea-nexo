package com.glea.nexo.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.glea.nexo.domain.inventory.Device;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByOrganization_IdAndDeviceUid(UUID organizationId, String deviceUid);
}
