package com.omnigalaxy.platform.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    String accessToken;
    long   expiresIn;
    Long   userId;
}
