package com.glea.nexo.api.controller.inventory;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.glea.nexo.api.dto.inventory.DeviceCreateRequestDto;
import com.glea.nexo.api.dto.inventory.DeviceResponseDto;
import com.glea.nexo.api.dto.inventory.DeviceUpdateRequestDto;
import com.glea.nexo.application.inventory.DeviceService;
import com.glea.nexo.domain.common.enums.OnlineState;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Inventory - Devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/zones/{zoneId}/devices")
    @Operation(summary = "Create device under a zone")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Device created"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Zone not found"),
            @ApiResponse(responseCode = "409", description = "Unique constraint conflict")
    })
    public ResponseEntity<DeviceResponseDto> createDevice(
            @PathVariable UUID zoneId,
            @Valid @RequestBody DeviceCreateRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.createDevice(zoneId, request));
    }

    @GetMapping("/devices")
    @Operation(summary = "List devices")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged devices"),
            @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<Page<DeviceResponseDto>> listDevices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) UUID farmId,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) OnlineState state,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(deviceService.listDevices(page, size, sort, farmId, zoneId, state, q));
    }

    @GetMapping("/devices/{deviceId}")
    @Operation(summary = "Get device by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device found"),
            @ApiResponse(responseCode = "404", description = "Device not found")
    })
    public ResponseEntity<DeviceResponseDto> getDevice(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(deviceService.getDevice(deviceId));
    }

    @PutMapping("/devices/{deviceId}")
    @Operation(summary = "Update device", description = "Updates mutable fields only (name/state). deviceUid is immutable and cannot be updated in this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device updated"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "409", description = "Unique constraint conflict")
    })
    public ResponseEntity<DeviceResponseDto> updateDevice(
            @PathVariable UUID deviceId,
            @Valid @RequestBody DeviceUpdateRequestDto request
    ) {
        return ResponseEntity.ok(deviceService.updateDevice(deviceId, request));
    }

    @DeleteMapping("/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete device")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Device deleted"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "409", description = "Reference conflict")
    })
    public void deleteDevice(@PathVariable UUID deviceId) {
        deviceService.deleteDevice(deviceId);
    }
}
