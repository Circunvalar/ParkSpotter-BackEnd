package com.ucentral.desarrollos.backendparkspotter.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.LoginRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.TokenPairResponse;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.JwtService;
import com.ucentral.desarrollos.backendparkspotter.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired AuthService authService;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
        roleRepository.deleteAll();
        roleRepository.save(role("ROLE_USER"));
        roleRepository.save(role("ROLE_ADMIN"));
    }

    @Test
    void duplicateEmailShouldFail() throws Exception {
        userRepository.save(user("dup@example.com", "Password123!"));

        String body = """
                {"email":"dup@example.com","password":"Password123!"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginShouldReturnTokenPair() {
        userRepository.save(user("login@example.com", "Password123!"));

        TokenPairResponse response = authService.login(new LoginRequest("login@example.com", "Password123!"), "browser");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void protectedEndpointWithoutTokenShouldBeUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleMismatchShouldBeForbidden() throws Exception {
        Role userRole = roleRepository.findByName("ROLE_USER").orElseThrow();
        UserAccount user = user("role@example.com", "Password123!");
        user.getRoles().add(userRole);
        userRepository.save(user);

        String jwt = jwtService.generateAccessToken(user.getId(), user.getEmail(), java.util.List.of("ROLE_USER"));

        mockMvc.perform(get("/api/v1/admin/secret")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    private UserAccount user(String email, String rawPassword) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setEnabled(true);
        return user;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
