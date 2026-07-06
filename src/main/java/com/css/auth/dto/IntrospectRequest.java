package com.css.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IntrospectRequest {

    @NotBlank
    private String token;

    private String clientId;
}
