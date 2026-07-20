package com.example.adminloyalty.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CreateCashierRepositoryTest {

    @Test
    public void fingerprintIsStableAndDoesNotRetainThePassword() {
        String payload = "{\"email\":\"cashier@example.com\",\"password\":\"secret1\"}";

        String first = CreateCashierRepository.fingerprint(payload);
        String repeat = CreateCashierRepository.fingerprint(payload);

        assertEquals(first, repeat);
        assertEquals(64, first.length());
        assertFalse(first.contains("secret1"));
    }
}
