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

import com.glea.nexo.api.dto.inventory.ZoneCreateRequestDto;
import com.glea.nexo.api.dto.inventory.ZoneResponseDto;
import com.glea.nexo.api.dto.inventory.ZoneUpdateRequestDto;
import com.glea.nexo.application.inventory.ZoneService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Inventory - Zones")
public class ZoneController {

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @PostMapping("/farms/{farmId}/zones")
    @Operation(summary = "Create zone under a farm")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Zone created"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Farm not found"),
            @ApiResponse(responseCode = "409", description = "Unique constraint conflict")
    })
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    public ResponseEntity<ZoneResponseDto> createZone(
            @Parameter(description = "Parent farm identifier", example = "d4620d12-97aa-49c4-afde-8cbcbf8472de") @PathVariable UUID farmId,
            @Valid @RequestBody ZoneCreateRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneService.createZone(farmId, request));
    }

    @GetMapping("/farms/{farmId}/zones")
    @Operation(summary = "List zones under a farm")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged zones"),
            @ApiResponse(responseCode = "404", description = "Farm not found")
    })
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    public ResponseEntity<Page<ZoneResponseDto>> listZones(
            @Parameter(description = "Parent farm identifier", example = "d4620d12-97aa-49c4-afde-8cbcbf8472de") @PathVariable UUID farmId,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort expression field,direction", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @Parameter(description = "Optional free-text filter", example = "zona norte")
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(zoneService.listZones(farmId, page, size, sort, q));
    }

    @GetMapping("/zones/{zoneId}")
    @Operation(summary = "Get zone by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zone found"),
            @ApiResponse(responseCode = "404", description = "Zone not found")
    })
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    public ResponseEntity<ZoneResponseDto> getZone(
            @Parameter(description = "Zone identifier", example = "41ef1c42-d2c3-4b6f-a702-e891be507f42") @PathVariable UUID zoneId) {
        return ResponseEntity.ok(zoneService.getZone(zoneId));
    }

    @PutMapping("/zones/{zoneId}")
    @Operation(summary = "Update zone")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zone updated"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Zone not found"),
            @ApiResponse(responseCode = "409", description = "Unique constraint conflict")
    })
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    public ResponseEntity<ZoneResponseDto> updateZone(
            @Parameter(description = "Zone identifier", example = "41ef1c42-d2c3-4b6f-a702-e891be507f42") @PathVariable UUID zoneId,
            @Valid @RequestBody ZoneUpdateRequestDto request
    ) {
        return ResponseEntity.ok(zoneService.updateZone(zoneId, request));
    }

    @DeleteMapping("/zones/{zoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete zone")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Zone deleted"),
            @ApiResponse(responseCode = "404", description = "Zone not found"),
            @ApiResponse(responseCode = "409", description = "Reference conflict")
    })
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    public void deleteZone(
            @Parameter(description = "Zone identifier", example = "41ef1c42-d2c3-4b6f-a702-e891be507f42") @PathVariable UUID zoneId) {
        zoneService.deleteZone(zoneId);
    }
}
