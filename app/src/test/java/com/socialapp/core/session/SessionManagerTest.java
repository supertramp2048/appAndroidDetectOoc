package com.socialapp.core.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.socialapp.core.network.dto.UserResource;

import org.junit.Test;

public class SessionManagerTest {

    @Test
    public void testSessionStateDataClass() {
        UserResource user = new UserResource();
        user.id = 1;
        user.name = "Test User";

        SessionManager.SessionState stateLoggedIn = new SessionManager.SessionState(true, user, "token123");
        assertTrue(stateLoggedIn.isLoggedIn);
        assertEquals("token123", stateLoggedIn.token);
        assertEquals("Test User", stateLoggedIn.user.name);

        SessionManager.SessionState stateLoggedOut = new SessionManager.SessionState(false, null, null);
        assertFalse(stateLoggedOut.isLoggedIn);
        assertNull(stateLoggedOut.token);
        assertNull(stateLoggedOut.user);
    }
}
