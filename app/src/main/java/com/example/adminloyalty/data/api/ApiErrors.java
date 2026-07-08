package com.example.adminloyalty.data.api;

/**
 * Central mapping for the API error codes that mean the same thing on every screen — transport
 * failures, rate limiting, and the shared fallback. Screen ViewModels handle their own domain
 * codes (e.g. {@code REDEEM_NOT_PENDING}) first, then delegate the rest here so the common
 * wording stays consistent and doesn't drift between paths.
 */
public final class ApiErrors {

    private ApiErrors() {}

    /**
     * User-facing message for the codes shared across screens. {@code forbiddenMsg} is the
     * screen-specific 403 wording (the required role differs per screen); {@code defaultMsg} is
     * the fallback when the backend sends no message and the code isn't one of the common ones.
     */
    public static String message(ApiResult r, String forbiddenMsg, String defaultMsg) {
        if (r.code == null) return defaultMsg;
        switch (r.code) {
            case "NETWORK_ERROR": return "No connection. Check your network and retry.";
            case "CLIENT_ERROR":  return "Could not build the request.";
            case "RATE_LIMITED":  return "Too many requests. Try again shortly.";
            case "FORBIDDEN":
            case "HTTP_403":      return forbiddenMsg;
            default:              return r.message != null ? r.message : defaultMsg;
        }
    }
}
