package com.glea.nexo.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glea.nexo.domain.inventory.Device;
import com.glea.nexo.domain.location.Farm;
import com.glea.nexo.domain.location.Organization;
import com.glea.nexo.domain.location.Zone;
import com.glea.nexo.domain.repository.DeviceRepository;
import com.glea.nexo.domain.repository.FarmRepository;
import com.glea.nexo.domain.repository.OrganizationRepository;
import com.glea.nexo.domain.repository.ZoneRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InventoryCrudIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    private Organization defaultOrganization;

    @BeforeEach
    void setup() {
        deviceRepository.deleteAll();
        zoneRepository.deleteAll();
        farmRepository.deleteAll();

        defaultOrganization = organizationRepository.findByCode("default")
                .orElseGet(() -> {
                    Organization organization = new Organization();
                    organization.setCode("default");
                    organization.setName("Default Organization");
                    return organizationRepository.saveAndFlush(organization);
                });
    }

    @Test
    void farmCreateOk() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/farms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"farm-01",
                                  "name":"Farm One",
                                  "location":{"lat":4.7,"lng":-74.1}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.code").value("farm-01"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID farmId = UUID.fromString(body.get("id").asText());

        Farm farm = farmRepository.findById(farmId).orElseThrow();
        assertThat(farm.getOrganization().getId()).isEqualTo(defaultOrganization.getId());
    }

    @Test
    void farmUniqueByOrgReturnsConflict() throws Exception {
        String payload = """
                {
                  "code":"farm-dup",
                  "name":"Farm Duplicate"
                }
                """;

        mockMvc.perform(post("/api/farms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/farms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void farmListPagedOk() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/farms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "code":"farm-pg-%d",
                                      "name":"Farm %d"
                                    }
                                    """.formatted(i, i)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/farms")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "code,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void zoneCreateAndUniquePerFarm() throws Exception {
        Farm farm = new Farm();
        farm.setOrganization(defaultOrganization);
        farm.setCode("farm-zone");
        farm.setName("Farm Zone");
        farm = farmRepository.saveAndFlush(farm);

        String payload = """
                {
                  "code":"zone-a",
                  "name":"Zona A",
                  "geometry":{"type":"Polygon"}
                }
                """;

        mockMvc.perform(post("/api/farms/{farmId}/zones", farm.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("zone-a"));

        mockMvc.perform(post("/api/farms/{farmId}/zones", farm.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void deviceCreateUnderZoneSetsOrgFarmZoneAndUniquePerOrg() throws Exception {
        Farm farm = new Farm();
        farm.setOrganization(defaultOrganization);
        farm.setCode("farm-dev");
        farm.setName("Farm Devices");
        farm = farmRepository.saveAndFlush(farm);

        Zone zone = new Zone();
        zone.setFarm(farm);
        zone.setCode("zone-dev");
        zone.setName("Zone Devices");
        zone = zoneRepository.saveAndFlush(zone);

        String payload = """
                {
                  "deviceUid":"dev-001",
                  "name":"Gateway 001"
                }
                """;

        MvcResult created = mockMvc.perform(post("/api/zones/{zoneId}/devices", zone.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceUid").value("dev-001"))
                .andReturn();

        UUID deviceId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        Device device = deviceRepository.findById(deviceId).orElseThrow();

        assertThat(device.getOrganization().getId()).isEqualTo(defaultOrganization.getId());
        assertThat(device.getFarm().getId()).isEqualTo(farm.getId());
        assertThat(device.getZone().getId()).isEqualTo(zone.getId());

        mockMvc.perform(post("/api/zones/{zoneId}/devices", zone.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void deviceUpdateShouldNotModifyDeviceUid() throws Exception {
        Farm farm = new Farm();
        farm.setOrganization(defaultOrganization);
        farm.setCode("farm-upd");
        farm.setName("Farm Update");
        farm = farmRepository.saveAndFlush(farm);

        Zone zone = new Zone();
        zone.setFarm(farm);
        zone.setCode("zone-upd");
        zone.setName("Zone Update");
        zone = zoneRepository.saveAndFlush(zone);

        Device device = new Device();
        device.setOrganization(defaultOrganization);
        device.setFarm(farm);
        device.setZone(zone);
        device.setDeviceUid("dev-fixed-001");
        device.setName("Old Name");
        device = deviceRepository.saveAndFlush(device);

        mockMvc.perform(put("/api/devices/{deviceId}", device.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"  Updated Device Name  ",
                                  "state":"ONLINE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceUid").value("dev-fixed-001"))
                .andExpect(jsonPath("$.name").value("Updated Device Name"))
                .andExpect(jsonPath("$.state").value("ONLINE"));

        Device reloaded = deviceRepository.findById(device.getId()).orElseThrow();
        assertThat(reloaded.getDeviceUid()).isEqualTo("dev-fixed-001");
        assertThat(reloaded.getName()).isEqualTo("Updated Device Name");
        assertThat(reloaded.getState().name()).isEqualTo("ONLINE");
    }

    @Test
    void deviceUpdateShouldFailWhenDeviceUidIsProvided() throws Exception {
        Farm farm = new Farm();
        farm.setOrganization(defaultOrganization);
        farm.setCode("farm-upd-err");
        farm.setName("Farm Update Error");
        farm = farmRepository.saveAndFlush(farm);

        Zone zone = new Zone();
        zone.setFarm(farm);
        zone.setCode("zone-upd-err");
        zone.setName("Zone Update Error");
        zone = zoneRepository.saveAndFlush(zone);

        Device device = new Device();
        device.setOrganization(defaultOrganization);
        device.setFarm(farm);
        device.setZone(zone);
        device.setDeviceUid("dev-fixed-002");
        device.setName("Original Name");
        device = deviceRepository.saveAndFlush(device);

        mockMvc.perform(put("/api/devices/{deviceId}", device.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceUid":"dev-hacked-999",
                                  "name":"Attempted Change"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        Device reloaded = deviceRepository.findById(device.getId()).orElseThrow();
        assertThat(reloaded.getDeviceUid()).isEqualTo("dev-fixed-002");
        assertThat(reloaded.getName()).isEqualTo("Original Name");
    }
}
