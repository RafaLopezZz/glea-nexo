package com.glea.nexo.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "System")
public class PingController {
    @GetMapping("/ping")
    @Operation(summary = "Simple availability probe", description = "Returns a plain 'ok' string when the backend is reachable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Backend reachable", content = @Content(examples = @ExampleObject(value = "ok")))
    })
    public String ping() {
        return "ok";
    }
}
