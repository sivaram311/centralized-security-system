package com.css.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    /** Registered application client ID, e.g. grok-dev, agent-platform */
    @NotBlank
    private String clientId;
}
