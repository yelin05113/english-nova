package com.nightfall.englishnova.shared.auth;

import com.nightfall.englishnova.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalAuthVerifierTest {

    private static final String SECRET = "test-internal-auth-secret";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-09T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void optionalReturnsVerifiedCurrentUser() {
        InternalAuthVerifier verifier = new InternalAuthVerifier(SECRET, 60, FIXED_CLOCK);
        MockHttpServletRequest request = signedRequest(
                "1001",
                "alice",
                Long.toString(FIXED_CLOCK.instant().getEpochSecond())
        );

        CurrentUser currentUser = verifier.optional(request);

        assertEquals(1001L, currentUser.id());
        assertEquals("alice", currentUser.username());
    }

    @Test
    void optionalReturnsNullForInvalidSignature() {
        InternalAuthVerifier verifier = new InternalAuthVerifier(SECRET, 60, FIXED_CLOCK);
        MockHttpServletRequest request = invalidSignatureRequest(
                "1001",
                "alice",
                Long.toString(FIXED_CLOCK.instant().getEpochSecond())
        );

        assertNull(verifier.optional(request));
    }

    @Test
    void optionalReturnsNullForExpiredTimestamp() {
        InternalAuthVerifier verifier = new InternalAuthVerifier(SECRET, 60, FIXED_CLOCK);
        MockHttpServletRequest request = signedRequest(
                "1001",
                "alice",
                Long.toString(FIXED_CLOCK.instant().minusSeconds(200).getEpochSecond())
        );

        assertNull(verifier.optional(request));
    }

    @Test
    void requireRejectsMissingHeaders() {
        InternalAuthVerifier verifier = new InternalAuthVerifier(SECRET, 60, FIXED_CLOCK);

        assertThrows(UnauthorizedException.class, () -> verifier.require(new MockHttpServletRequest()));
    }

    private MockHttpServletRequest signedRequest(String userId, String username, String timestamp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthHeaders.USER_ID, userId);
        request.addHeader(AuthHeaders.USERNAME, username);
        request.addHeader(AuthHeaders.TIMESTAMP, timestamp);
        request.addHeader(
                AuthHeaders.SIGNATURE,
                InternalAuthSignatureUtils.sign(SECRET, userId, username, timestamp)
        );
        return request;
    }

    private MockHttpServletRequest invalidSignatureRequest(String userId, String username, String timestamp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthHeaders.USER_ID, userId);
        request.addHeader(AuthHeaders.USERNAME, username);
        request.addHeader(AuthHeaders.TIMESTAMP, timestamp);
        request.addHeader(AuthHeaders.SIGNATURE, "tampered");
        return request;
    }
}
