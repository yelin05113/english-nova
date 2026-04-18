package com.nightfall.englishnova.auth.service;

public interface JwtTokenService {

    String issueToken(long userId, String username);
}
