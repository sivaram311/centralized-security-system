package com.css.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration coverage for the OIDC-shaped Authorization Code + PKCE flow
 * (ADR 001, Phase 2): GET /oauth/authorize, POST /oauth/login, POST /oauth/token.
 * Runs against the H2 in-memory database and seeded default accounts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthAuthorizeIT {

    private static final String CLIENT_ID = "agent-portal";
    private static final String REDIRECT_URI = "http://localhost:5555/callback";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authorizeWithoutSsoCookieRedirectsToLoginPage() throws Exception {
        String challenge = challengeFor(generateVerifier());

        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith("/oauth/login")));
    }

    @Test
    void loginWithValidCredentialsAndPkceIssuesCodeAndSsoCookie() throws Exception {
        String verifier = generateVerifier();
        String challenge = challengeFor(verifier);

        MvcResult result = mockMvc.perform(post("/oauth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin")
                        .param("password", "admin123")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256")
                        .param("state", "xyz"))
                .andExpect(status().isFound())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).isNotNull().startsWith(REDIRECT_URI).contains("code=").contains("state=xyz");

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("CSS_SSO");
    }

    @Test
    void tokenExchangeWithCorrectVerifierReturnsTokens() throws Exception {
        String verifier = generateVerifier();
        String challenge = challengeFor(verifier);
        String code = extractCode(loginAndGetLocation(verifier, challenge, "state1"));

        MvcResult tokenResult = mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        assertThat(json.get("accessToken").asText()).isNotBlank();
        assertThat(json.get("refreshToken").asText()).isNotBlank();
        assertThat(json.get("clientId").asText()).isEqualTo(CLIENT_ID);
        assertThat(json.get("roles").isArray()).isTrue();
        assertThat(json.get("roles")).isNotEmpty();
    }

    @Test
    void tokenExchangeWithWrongVerifierIsRejected() throws Exception {
        String verifier = generateVerifier();
        String challenge = challengeFor(verifier);
        String code = extractCode(loginAndGetLocation(verifier, challenge, "state2"));

        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "totally-wrong-verifier-value-1234567890"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void secondAuthorizeWithSsoCookieIssuesCodeWithoutPassword() throws Exception {
        String verifier1 = generateVerifier();
        String challenge1 = challengeFor(verifier1);

        MvcResult loginResult = mockMvc.perform(post("/oauth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin")
                        .param("password", "admin123")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_challenge", challenge1)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isFound())
                .andReturn();

        Cookie ssoCookie = loginResult.getResponse().getCookie("CSS_SSO");
        assertThat(ssoCookie).isNotNull();

        String verifier2 = generateVerifier();
        String challenge2 = challengeFor(verifier2);

        MvcResult secondAuthorize = mockMvc.perform(get("/oauth/authorize")
                        .cookie(ssoCookie)
                        .param("response_type", "code")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_challenge", challenge2)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith(REDIRECT_URI)))
                .andReturn();

        String secondCode = extractCode(secondAuthorize.getResponse().getHeader("Location"));
        assertThat(secondCode).isNotBlank();

        MvcResult tokenResult = mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", secondCode)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", verifier2))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        assertThat(json.get("accessToken").asText()).isNotBlank();
    }

    @Test
    void authorizeWithUnknownClientIdIsRejected() throws Exception {
        String challenge = challengeFor(generateVerifier());

        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", "no-such-client")
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is4xxClientError());
    }

    private String loginAndGetLocation(String verifier, String challenge, String state) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin")
                        .param("password", "admin123")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256")
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn();
        return result.getResponse().getHeader("Location");
    }

    private String extractCode(String location) {
        int idx = location.indexOf("code=");
        String rest = location.substring(idx + "code=".length());
        int amp = rest.indexOf('&');
        return amp >= 0 ? rest.substring(0, amp) : rest;
    }

    private String generateVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String challengeFor(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
