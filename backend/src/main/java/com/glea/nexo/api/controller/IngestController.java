package com.glea.nexo.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.glea.nexo.api.dto.ingest.IngestBatchRequestDto;
import com.glea.nexo.api.dto.ingest.IngestBatchResponseDto;
import com.glea.nexo.api.error.ApiErrorResponse;
import com.glea.nexo.application.ingest.IngestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ingest/readings")
@Tag(name = "Ingest")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/batch")
    @Operation(summary = "Ingest telemetry readings in batch",
            description = "Each reading must include ts as the event time in ISO-8601 UTC. Requests with missing or invalid ts are rejected with a controlled 400 response.")
    @Parameters({
            @Parameter(ref = "#/components/parameters/XOrgCodeHeader")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Batch payload for telemetry ingest.", content = @Content(schema = @Schema(implementation = IngestBatchRequestDto.class), examples = {
            @ExampleObject(name = "Single reading", value = """
                    {
                      \"source\": \"manual-dedupe-test-rpi\",
                      \"topic\": \"agro/finca1/zona1/pi-gw-001/sensor/soil-01/SOIL_MOISTURE/telemetry\",
                      \"readings\": [
                        {
                          \"messageId\": \"dedupe-001\",
                          \"deviceId\": \"pi-gw-001:soil-01\",
                          \"ts\": \"2026-01-01T00:00:00Z\",
                          \"value\": 25.0,
                          \"unit\": \"%VWC\",
                          \"rssi\": -55,
                          \"battery\": 3.78
                        }
                      ]
                    }
                    """)
    }))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch processed. Items may be processed, duplicate or error.", content = @Content(schema = @Schema(implementation = IngestBatchResponseDto.class), examples = {
                    @ExampleObject(name = "Processed", value = """
                            {
                              \"total\":1,
                              \"processed\":1,
                              \"duplicates\":0,
                              \"errors\":0,
                              \"items\":[{"index":0,"messageId":"dedupe-001","status":"PROCESSED","detail":"telemetry reading persisted"}]
                            }
                            """),
                    @ExampleObject(name = "Duplicate", value = """
                            {
                              \"total\":1,
                              \"processed\":0,
                              \"duplicates\":1,
                              \"errors\":0,
                              \"items\":[{"index":0,"messageId":"dedupe-001","status":"DUPLICATE","detail":"duplicate ingest event by exists check"}]
                            }
                            """)
            })),
            @ApiResponse(responseCode = "400", description = "Validation error or malformed payload", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Organization, farm or zone could not be resolved", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Constraint conflict", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<IngestBatchResponseDto> ingestBatch(@Valid @RequestBody IngestBatchRequestDto request) {
        return ResponseEntity.ok(ingestService.ingestBatch(request));
    }
}
