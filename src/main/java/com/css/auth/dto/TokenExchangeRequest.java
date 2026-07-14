package com.css.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** Body of POST /oauth/token when sent as application/json. */
@Getter
@Setter
public class TokenExchangeRequest {

    @JsonProperty("grant_type")
    private String grantType;

    private String code;

    @JsonProperty("redirect_uri")
    private String redirectUri;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("code_verifier")
    private String codeVerifier;
}
