package com.ucentral.desarrollos.backendparkspotter.user;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.notification.entity.NotificationType;
import com.ucentral.desarrollos.backendparkspotter.notification.repository.DeviceTokenRepository;
import com.ucentral.desarrollos.backendparkspotter.notification.service.EmailSender;
import com.ucentral.desarrollos.backendparkspotter.notification.service.NotificationService;
import com.ucentral.desarrollos.backendparkspotter.notification.service.PushSender;
import com.ucentral.desarrollos.backendparkspotter.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cuenta (Sprint 06): perfil, recuperación de contraseña, eliminación y notificaciones con push y correo. */
class AccountApiTest extends ApiTestSupport {

    @MockitoBean EmailSender emailSender;
    @MockitoBean PushSender pushSender;
    @Autowired NotificationService notificationService;
    @Autowired DeviceTokenRepository deviceTokenRepository;

    // ---------- perfil ----------

    @Test
    void profile_GetUpdateAndAddRole() throws Exception {
        UserAccount driver = driver("perfil@parkspotter.co");
        perform(get("/api/v1/users/me"), driver)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("perfil@parkspotter.co"));
        performJson(patch("/api/v1/users/me"), driver, "{\"fullName\":\"  Ana María Pérez \",\"phone\":\"3001234567\"}")
                .andExpect(jsonPath("$.fullName").value("Ana María Pérez"))
                .andExpect(jsonPath("$.phone").value("3001234567"));
        performJson(patch("/api/v1/users/me"), driver, "{\"phone\":\"\"}")
                .andExpect(jsonPath("$.fullName").value("Ana María Pérez"))
                .andExpect(jsonPath("$.phone").doesNotExist());
        performJson(patch("/api/v1/users/me"), driver, "{\"phone\":\"abc\"}").andExpect(status().isBadRequest());
        performJson(post("/api/v1/users/me/roles"), driver, "{\"role\":\"OWNER\"}")
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ROLE_DRIVER", "ROLE_OWNER", "ROLE_USER")));
    }

    @Test
    void deleteAccount_ChecksPasswordAndActiveReservations_ThenAnonymizes() throws Exception {
        UserAccount owner = owner("borrar-dueno@parkspotter.co");
        UserAccount driver = driver("borrar-conductor@parkspotter.co");
        GarageResponse garage = garage24h(owner, "Garaje Para Borrar", 2);
        String reservationId = reserveId(driver, garage.id(), 60, 120);
        performJson(post("/api/v1/notifications/devices"), driver, "{\"token\":\"tok-borrar\",\"platform\":\"ANDROID\"}");

        performJson(delete("/api/v1/users/me"), driver, "{\"password\":\"incorrecta\"}").andExpect(status().isBadRequest());
        performJson(delete("/api/v1/users/me"), driver, "{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isConflict());
        performJson(post("/api/v1/users/me/delete"), owner, "{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isConflict());

        performJson(post("/api/v1/reservations/" + reservationId + "/cancel"), driver, "{}").andExpect(status().isOk());
        performJson(post("/api/v1/users/me/delete"), driver, "{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isNoContent());
        performJson(delete("/api/v1/users/me"), owner, "{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isNoContent());

        UserAccount deleted = userRepository.findById(driver.getId()).orElseThrow();
        assertThat(deleted.getEmail()).endsWith("@deleted.parkspotter.invalid");
        assertThat(deleted.isEnabled()).isFalse();
        assertThat(deviceTokenRepository.findByToken("tok-borrar")).isEmpty();
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/detail"))
                .andExpect(jsonPath("$.availability.status").value("INACTIVE"));
        // El correo queda libre para registrarse otra vez.
        performJson(post("/api/v1/auth/register"), null,
                "{\"email\":\"borrar-conductor@parkspotter.co\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk());
    }

    // ---------- contraseña ----------

    @Test
    void passwordReset_FullFlow_TokenIsSingleUse() throws Exception {
        driver("olvido@parkspotter.co");
        performJson(post("/api/v1/auth/password/forgot"), null, "{\"email\":\"Olvido@ParkSpotter.co\"}")
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("olvido@parkspotter.co"), anyString(), body.capture());
        Matcher matcher = Pattern.compile("código: (\\S+)").matcher(body.getValue());
        assertThat(matcher.find()).isTrue();
        String token = matcher.group(1);

        String reset = "{\"token\":\"" + token + "\",\"newPassword\":\"NuevaClave123!\"}";
        performJson(post("/api/v1/auth/password/reset"), null, reset).andExpect(status().isNoContent());
        performJson(post("/api/v1/auth/password/reset"), null, reset).andExpect(status().isBadRequest());
        performJson(post("/api/v1/auth/login"), null, "{\"email\":\"olvido@parkspotter.co\",\"password\":\"NuevaClave123!\"}")
                .andExpect(status().isOk());
    }

    @Test
    void passwordReset_UnknownOrDisabledEmail_IsSilent_AndRateLimited() throws Exception {
        UserAccount disabled = driver("deshabilitado@parkspotter.co");
        disabled.setEnabled(false);
        userRepository.save(disabled);

        performJson(post("/api/v1/auth/password/forgot"), null, "{\"email\":\"deshabilitado@parkspotter.co\"}")
                .andExpect(status().isAccepted());
        for (int i = 0; i < 5; i++) {
            performJson(post("/api/v1/auth/password/forgot"), null, "{\"email\":\"nadie@parkspotter.co\"}")
                    .andExpect(status().isAccepted());
        }
        performJson(post("/api/v1/auth/password/forgot"), null, "{\"email\":\"nadie@parkspotter.co\"}")
                .andExpect(status().isTooManyRequests());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());

        performJson(post("/api/v1/auth/password/reset"), null, "{\"token\":\"tokeninventado123\",\"newPassword\":\"NuevaClave123!\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordReset_TokenOfDisabledUser_IsRejected() throws Exception {
        UserAccount user = driver("luego-deshabilitado@parkspotter.co");
        performJson(post("/api/v1/auth/password/forgot"), null, "{\"email\":\"luego-deshabilitado@parkspotter.co\"}");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(anyString(), anyString(), body.capture());
        Matcher matcher = Pattern.compile("código: (\\S+)").matcher(body.getValue());
        assertThat(matcher.find()).isTrue();
        user = userRepository.findById(user.getId()).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);

        performJson(post("/api/v1/auth/password/reset"), null,
                "{\"token\":\"" + matcher.group(1) + "\",\"newPassword\":\"NuevaClave123!\"}")
                .andExpect(status().isBadRequest());
    }

    // ---------- notificaciones ----------

    @Test
    void notifications_InboxReadAndDevices() throws Exception {
        UserAccount user = driver("bandeja@parkspotter.co");
        UserAccount other = driver("otra-bandeja@parkspotter.co");
        when(pushSender.send(eq("tok-malo"), anyString(), anyString(), anyMap())).thenReturn(PushSender.Result.INVALID_TOKEN);
        when(pushSender.send(eq("tok-bueno"), anyString(), anyString(), anyMap())).thenReturn(PushSender.Result.SENT);

        performJson(post("/api/v1/notifications/devices"), user, "{\"token\":\"tok-malo\",\"platform\":\"ANDROID\"}")
                .andExpect(status().isNoContent());
        performJson(post("/api/v1/notifications/devices"), user, "{\"token\":\"tok-bueno\",\"platform\":\"IOS\"}")
                .andExpect(status().isNoContent());
        performJson(post("/api/v1/notifications/devices"), user, "{\"token\":\"tok bueno\",\"platform\":\"IOS\"}")
                .andExpect(status().isBadRequest());

        UUID reference = UUID.randomUUID();
        notificationService.notify(user, NotificationType.GARAGE_VERIFIED, "Título", "x".repeat(600), reference, true);
        notificationService.notify(user, NotificationType.NEW_REVIEW, "Otra", "Cuerpo", null, false);
        notificationService.notify(null, NotificationType.NEW_REVIEW, "Nadie", "Cuerpo", null, false);

        verify(pushSender).send(eq("tok-bueno"), eq("Título"), anyString(), any());
        verify(emailSender).send(eq("bandeja@parkspotter.co"), eq("Título"), anyString());
        assertThat(deviceTokenRepository.findByToken("tok-malo")).isEmpty();

        perform(get("/api/v1/notifications/unread-count"), user).andExpect(jsonPath("$.unread").value(2));
        String id = read(perform(get("/api/v1/notifications"), user)
                .andExpect(jsonPath("$.content", hasSize(2))), "$.content[1].id");
        perform(patch("/api/v1/notifications/" + id + "/read"), user)
                .andExpect(jsonPath("$.read").value(true));
        perform(patch("/api/v1/notifications/" + id + "/read"), user).andExpect(jsonPath("$.read").value(true));
        perform(patch("/api/v1/notifications/" + id + "/read"), other).andExpect(status().isNotFound());
        perform(get("/api/v1/notifications").param("unreadOnly", "true"), user).andExpect(jsonPath("$.content", hasSize(1)));
        perform(post("/api/v1/notifications/read-all"), user).andExpect(status().isNoContent());
        perform(get("/api/v1/notifications/unread-count"), user).andExpect(jsonPath("$.unread").value(0));

        perform(delete("/api/v1/notifications/devices").param("token", "tok-bueno"), user).andExpect(status().isNoContent());
        assertThat(deviceTokenRepository.findByToken("tok-bueno")).isEmpty();
    }

    @Test
    void notifications_PushFailureDoesNotBreakTheCaller() {
        UserAccount user = driver("falla-push@parkspotter.co");
        notificationService.registerDevice(new com.ucentral.desarrollos.backendparkspotter.notification.dto.DeviceTokenRequest(
                "tok-falla", com.ucentral.desarrollos.backendparkspotter.notification.entity.DevicePlatform.WEB), user);
        when(pushSender.send(anyString(), anyString(), anyString(), anyMap())).thenThrow(new IllegalStateException("caído"));

        notificationService.notify(user, NotificationType.NEW_REVIEW, "T", "B", null, false);
        assertThat(deviceTokenRepository.findByToken("tok-falla")).isPresent();
    }
}
