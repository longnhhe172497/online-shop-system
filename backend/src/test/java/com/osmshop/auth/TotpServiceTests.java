package com.osmshop.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TotpServiceTests {
    @TempDir Path directory;

    @Test
    void followsRfc6238VectorAndEncryptsSecretAtRest() {
        TotpService service = new TotpService(directory.resolve("totp.key").toString());
        String secret = TotpService.encodeBase32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        assertEquals("287082", service.codeAtStep(secret, 59 / 30));
        assertEquals(1, service.validStep(secret, "287082", Instant.ofEpochSecond(59), null));
        assertEquals(-1, service.validStep(secret, "287082", Instant.ofEpochSecond(59), 1L));
        String encrypted = service.encrypt(secret);
        assertNotEquals(secret, encrypted);
        assertEquals(secret, service.decrypt(encrypted));
    }
}
