package com.css.auth;

import com.css.auth.dto.LoginRequest;
import com.css.auth.dto.RefreshTokenRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

/**
 * Integration coverage for the Auth API (login/refresh/logout) against the
 * H2 in-memory database and seeded default accounts (admin/admin123, demo/demo123).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIT {

    private static final String CLIENT_ID = "agent-portal";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginWithValidCredentialsReturnsTokensAndRoles() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "admin123", CLIENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles").isNotEmpty());
    }

    @Test
    void loginWithBadPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "wrong-password", CLIENT_ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownClientIdReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "admin123", "no-such-client")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithValidTokenReturnsNewAccessToken() throws Exception {
        JsonNode tokens = login("admin", "admin123", CLIENT_ID);
        String refreshToken = tokens.get("refreshToken").asText();

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken, CLIENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID))
                .andReturn();

        String newAccessToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        assertThat(newAccessToken).isNotBlank();
    }

    @Test
    void refreshWithBadTokenReturnsForbidden() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson("not-a-real-refresh-token", CLIENT_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRevokesRefreshTokenSoSubsequentRefreshFails() throws Exception {
        JsonNode tokens = login("demo", "demo123", CLIENT_ID);
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("clientId", CLIENT_ID))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken, CLIENT_ID)))
                .andExpect(status().isForbidden());
    }

    private JsonNode login(String username, String password, String clientId) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password, clientId)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String loginJson(String username, String password, String clientId) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setClientId(clientId);
        return objectMapper.writeValueAsString(request);
    }

    private String refreshJson(String refreshToken, String clientId) throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);
        request.setClientId(clientId);
        return objectMapper.writeValueAsString(request);
    }
}
