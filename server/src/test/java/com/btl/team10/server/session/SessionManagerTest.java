package com.btl.team10.server.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
    }

    @Test
    void testCreateSessionAndRetrieve() {
        var session = sessionManager.createSession(1L, "test_seller", "SELLER");

        assertNotNull(session.token());
        assertEquals(1L, session.userId());
        assertEquals("test_seller", session.account());
        assertEquals("SELLER", session.role());

        var retrieved = sessionManager.getSession(session.token());
        assertTrue(retrieved.isPresent());
        assertEquals(session.token(), retrieved.get().token());
    }

    @Test
    void testRoleAuthorization() {
        var session = sessionManager.createSession(2L, "test_bidder", "BIDDER");

        assertTrue(sessionManager.hasRole(session.token(), "BIDDER"));
        assertFalse(sessionManager.hasRole(session.token(), "SELLER"));
        assertFalse(sessionManager.hasRole(session.token(), "ADMIN"));
    }

    @Test
    void testRemoveSession() {
        var session = sessionManager.createSession(3L, "test_user", "BIDDER");
        sessionManager.removeSession(session.token());

        assertTrue(sessionManager.getSession(session.token()).isEmpty());
        assertFalse(sessionManager.hasRole(session.token(), "BIDDER"));
    }
}