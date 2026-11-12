package me.rexsystems.rexranks.editor;

import com.google.gson.JsonObject;

import java.util.UUID;

public final class EditorSession {
    public final String token;
    public final String applyCode;
    public final String createdBy;
    public final long expiresAt;
    public final String focus; // "all", "user:uuid", "group:name"
    public JsonObject pendingApply;
    public JsonObject lastDiff;

    public EditorSession(String token, String applyCode, String createdBy, long expiresAt, String focus) {
        this.token = token;
        this.applyCode = applyCode;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
        this.focus = focus;
    }

    public boolean expired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public UUID uuid() {
        // unused helper reserved for future per-session ownership checks
        return null;
    }
}
