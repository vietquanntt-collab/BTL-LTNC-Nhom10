package com.btl.team10.server.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientCommandParsingTest {

    @Test
    void testCommandTokenSplitting() {
        String rawLogin = "LOGIN|seller1|secretpass";
        String[] parts = rawLogin.split("\\|", -1);

        assertEquals(3, parts.length);
        assertEquals("LOGIN", parts[0]);
        assertEquals("seller1", parts[1]);
        assertEquals("secretpass", parts[2]);
    }

    @Test
    void testCreateItemCommandSplitting() {
        String raw = "CREATE_ITEM|token-uuid-123|Charizard Card|CARD|Mint condition";
        String[] parts = raw.split("\\|", -1);

        assertEquals(5, parts.length);
        assertEquals("CREATE_ITEM", parts[0]);
        assertEquals("token-uuid-123", parts[1]);
        assertEquals("Charizard Card", parts[2]);
        assertEquals("CARD", parts[3]);
        assertEquals("Mint condition", parts[4]);
    }
}