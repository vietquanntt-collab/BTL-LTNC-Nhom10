package com.btl.n8.Controller;

import com.btl.n8.Model.Entity.User;

public class SessionManager {

    private static volatile SessionManager instance;
    private volatile User currentUser;
    private volatile String sessionId; // thêm sessionId cho socket

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    public void setCurrentUser(User user) { this.currentUser = user; }
    public User getCurrentUser()          { return currentUser; }

    public void setSessionId(String id)   { this.sessionId = id; }
    public String getSessionId()          { return sessionId; }

    public void logout() {
        this.currentUser = null;
        this.sessionId   = null;
    }

    public boolean isLoggedIn() { return currentUser != null; }
}