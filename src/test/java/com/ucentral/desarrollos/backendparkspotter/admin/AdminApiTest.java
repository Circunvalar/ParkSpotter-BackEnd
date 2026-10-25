package com.ucentral.desarrollos.backendparkspotter.admin;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Panel de administración (Sprint 06): usuarios, fotos y verificación de garajes. */
class AdminApiTest extends ApiTestSupport {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    UserAccount admin;
    UserAccount owner;
    UserAccount driver;
    GarageResponse garage;

    @BeforeEach
    void setUp() {
        admin = admin("admin@parkspotter.co");
        owner = owner("dueno-admin@parkspotter.co");
        driver = driver("conductor-admin@parkspotter.co");
        garage = garage24h(owner, "Garaje Verificable", 2);
    }

    // ---------- usuarios ----------

    @Test
    void users_SearchByTextRoleAndStatus() throws Exception {
        perform(get("/api/v1/admin/users"), admin).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(3)));
        perform(get("/api/v1/admin/users").param("q", "DUENO"), admin).andExpect(jsonPath("$.content", hasSize(1)));
        perform(get("/api/v1/admin/users").param("role", "driver"), admin)
                .andExpect(jsonPath("$.content[0].email").value("conductor-admin@parkspotter.co"));
        perform(get("/api/v1/admin/users").param("role", "ROLE_ADMIN").param("enabled", "true"), admin)
                .andExpect(jsonPath("$.content", hasSize(1)));
        perform(get("/api/v1/admin/users").param("role", "pirata"), admin).andExpect(status().isBadRequest());
        perform(get("/api/v1/admin/users"), driver).andExpect(status().isForbidden());
    }

    @Test
    void setStatus_DisablesUser_ButNotYourself() throws Exception {
        performJson(patch("/api/v1/admin/users/" + driver.getId() + "/status"), admin, "{\"enabled\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        performJson(patch("/api/v1/admin/users/" + driver.getId() + "/status"), admin, "{\"enabled\":true}")
                .andExpect(jsonPath("$.enabled").value(true));
        performJson(patch("/api/v1/admin/users/" + admin.getId() + "/status"), admin, "{\"enabled\":false}")
                .andExpect(status().isBadRequest());
        performJson(patch("/api/v1/admin/users/" + UUID.randomUUID() + "/status"), admin, "{\"enabled\":false}")
                .andExpect(status().isNotFound());
    }

    @Test
    void setRoles_ReplacesRoles_KeepsUser_AndProtectsOwnAdmin() throws Exception {
        performJson(put("/api/v1/admin/users/" + driver.getId() + "/roles"), admin, "{\"roles\":[\"OWNER\",\"DRIVER\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ROLE_DRIVER", "ROLE_OWNER", "ROLE_USER")));
        performJson(put("/api/v1/admin/users/" + admin.getId() + "/roles"), admin, "{\"roles\":[\"DRIVER\"]}")
                .andExpect(status().isBadRequest());
        performJson(put("/api/v1/admin/users/" + admin.getId() + "/roles"), admin, "{\"roles\":[\"ADMIN\",\"OWNER\"]}")
                .andExpect(status().isOk());
    }

    // ---------- fotos y verificación ----------

    @Test
    void photos_UploadListImageAndDelete() throws Exception {
        String photoId = read(upload(owner, "foto.png", PNG).andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png")), "$.id");
        upload(owner, "foto.jpg", JPEG).andExpect(jsonPath("$.contentType").value("image/jpeg"));
        upload(admin, "foto.webp", WEBP).andExpect(jsonPath("$.contentType").value("image/webp"));

        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/photos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/photos/" + photoId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/detail"))
                .andExpect(jsonPath("$.photos", hasSize(3)));

        perform(delete("/api/v1/garages/" + garage.id() + "/photos/" + photoId), driver).andExpect(status().isForbidden());
        perform(delete("/api/v1/garages/" + garage.id() + "/photos/" + photoId), owner).andExpect(status().isNoContent());
        perform(delete("/api/v1/garages/" + garage.id() + "/photos/" + photoId), owner).andExpect(status().isNotFound());
        perform(delete("/api/v1/garages/" + UUID.randomUUID() + "/photos/" + photoId), owner).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/photos/" + photoId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/garages/" + UUID.randomUUID() + "/photos")).andExpect(status().isNotFound());
    }

    @Test
    void photos_RejectWrongTypeEmptyForeignAndTooMany() throws Exception {
        upload(owner, "nota.txt", "hola mundo".getBytes()).andExpect(status().isBadRequest());
        upload(owner, "vacia.png", new byte[0]).andExpect(status().isBadRequest());
        upload(driver, "foto.png", PNG).andExpect(status().isForbidden());
        perform(multipart("/api/v1/garages/" + UUID.randomUUID() + "/photos")
                .file(new MockMultipartFile("file", "f.png", "image/png", PNG)), owner).andExpect(status().isNotFound());
        for (int i = 0; i < 8; i++) {
            upload(owner, "foto" + i + ".png", PNG).andExpect(status().isCreated());
        }
        upload(owner, "novena.png", PNG).andExpect(status().isBadRequest());
    }

    @Test
    void photos_RejectFileOverFiveMegabytes() throws Exception {
        byte[] big = Arrays.copyOf(PNG, 5 * 1024 * 1024 + 1);
        upload(owner, "grande.png", big).andExpect(status().isBadRequest());
    }

    @Test
    void verification_RequestApproveRejectFlow() throws Exception {
        perform(post("/api/v1/garages/" + garage.id() + "/verification-request"), owner).andExpect(status().isBadRequest());
        upload(owner, "foto.png", PNG);
        perform(post("/api/v1/garages/" + garage.id() + "/verification-request"), driver).andExpect(status().isForbidden());
        perform(post("/api/v1/garages/" + garage.id() + "/verification-request"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
        perform(post("/api/v1/garages/" + garage.id() + "/verification-request"), owner).andExpect(status().isConflict());

        perform(get("/api/v1/admin/garages"), admin).andExpect(jsonPath("$.content", hasSize(1)));
        performJson(post("/api/v1/admin/garages/" + garage.id() + "/verification"), admin,
                "{\"approved\":false,\"notes\":\"  Fotos borrosas \"}")
                .andExpect(jsonPath("$.verificationStatus").value("REJECTED"));
        perform(get("/api/v1/notifications"), owner)
                .andExpect(jsonPath("$.content[0].type").value("GARAGE_REJECTED"))
                .andExpect(jsonPath("$.content[0].body").value("La verificación de \"Garaje Verificable\" fue rechazada: Fotos borrosas"));

        perform(post("/api/v1/garages/" + garage.id() + "/verification-request"), owner).andExpect(status().isOk());
        performJson(post("/api/v1/admin/garages/" + garage.id() + "/verification"), admin, "{\"approved\":false,\"notes\":\" \"}")
                .andExpect(jsonPath("$.verificationStatus").value("REJECTED"));
        performJson(post("/api/v1/admin/garages/" + garage.id() + "/verification"), admin, "{\"approved\":true}")
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.verified").value(true));
        perform(post("/api/v1/garages/" + garage.id() + "/verification-request"), owner).andExpect(status().isConflict());
        perform(get("/api/v1/admin/garages").param("verificationStatus", "VERIFIED"), admin).andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(get("/api/v1/garages/search").param("verifiedOnly", "true"))
                .andExpect(jsonPath("$.content[0].verified").value(true));

        performJson(post("/api/v1/admin/garages/" + UUID.randomUUID() + "/verification"), admin, "{\"approved\":true}")
                .andExpect(status().isNotFound());
        performJson(post("/api/v1/admin/garages/" + garage.id() + "/verification"), owner, "{\"approved\":true}")
                .andExpect(status().isForbidden());
    }

    private ResultActions upload(UserAccount user, String name, byte[] bytes) throws Exception {
        return perform(multipart("/api/v1/garages/" + garage.id() + "/photos")
                .file(new MockMultipartFile("file", name, "application/octet-stream", bytes)), user);
    }
}
