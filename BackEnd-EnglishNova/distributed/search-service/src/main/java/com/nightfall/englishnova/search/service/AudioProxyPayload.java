package com.nightfall.englishnova.search.service;

public record AudioProxyPayload(
        byte[] content,
        String contentType
) {
}
