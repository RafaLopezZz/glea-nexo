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

import com.glea.nexo.api.dto.inventory.FarmCreateRequestDto;
import com.glea.nexo.api.dto.inventory.FarmResponseDto;
import com.glea.nexo.api.dto.inventory.FarmUpdateRequestDto;
import com.glea.nexo.application.inventory.FarmService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/farms")
@Tag(name = "Inventory - Farms")
public class FarmController {

    private final FarmService farmService;

    public FarmController(FarmService farmService) {
        this.farmService = farmService;
    }

    @PostMapping
    @Operation(summary = "Create farm")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Farm created"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "409", description = "Unique constraint conflict")
    })
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    public ResponseEntity<FarmResponseDto> createFarm(@Valid @RequestBody FarmCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(farmService.createFarm(request));
    }

    @GetMapping
    @Operation(summary = "List farms")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged farms"),
            @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    public ResponseEntity<Page<FarmResponseDto>> listFarms(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort expression field,direction", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @Parameter(description = "Optional free-text filter", example = "finca norte")
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(farmService.listFarms(page, size, sort, q));
    }

    @GetMapping("/{farmId}")
    @Operation(summary = "Get farm by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Farm found"),
            @ApiResponse(responseCode = "404", description = "Farm not found")
    })
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    public ResponseEntity<FarmResponseDto> getFarm(
            @Parameter(description = "Farm identifier", example = "d4620d12-97aa-49c4-afde-8cbcbf8472de") @PathVariable UUID farmId) {
        return ResponseEntity.ok(farmService.getFarm(farmId));
    }

    @PutMapping("/{farmId}")
    @Operation(summary = "Update farm")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Farm updated"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Farm not found"),
            @ApiResponse(responseCode = "409", description = "Unique constraint conflict")
    })
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    public ResponseEntity<FarmResponseDto> updateFarm(
            @Parameter(description = "Farm identifier", example = "d4620d12-97aa-49c4-afde-8cbcbf8472de") @PathVariable UUID farmId,
            @Valid @RequestBody FarmUpdateRequestDto request
    ) {
        return ResponseEntity.ok(farmService.updateFarm(farmId, request));
    }

    @DeleteMapping("/{farmId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete farm")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Farm deleted"),
            @ApiResponse(responseCode = "404", description = "Farm not found"),
            @ApiResponse(responseCode = "409", description = "Reference conflict")
    })
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    public void deleteFarm(
            @Parameter(description = "Farm identifier", example = "d4620d12-97aa-49c4-afde-8cbcbf8472de") @PathVariable UUID farmId) {
        farmService.deleteFarm(farmId);
    }
}
