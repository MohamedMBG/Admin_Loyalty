package com.example.adminloyalty.data.api;

import org.json.JSONObject;

import okhttp3.Response;

/**
 * Parsed backend envelope: {ok:true, data:...} or {ok:false, code, message}.
 * On 429 also carries Retry-After (seconds). Consult isOk() before reading data().
 */
public class ApiResult {

    public final boolean ok;
    public final int httpStatus;
    public final JSONObject data;   // present when ok
    public final String code;       // present when !ok — e.g. INSUFFICIENT_POINTS
    public final String message;    // human-ish message from backend
    public final int retryAfterSec; // 0 if absent

    private ApiResult(boolean ok, int httpStatus, JSONObject data, String code, String message, int retryAfterSec) {
        this.ok = ok;
        this.httpStatus = httpStatus;
        this.data = data;
        this.code = code;
        this.message = message;
        this.retryAfterSec = retryAfterSec;
    }

    public boolean isOk() {
        return ok;
    }

    /** Parse an OkHttp response body against the ApiResponse/ApiError envelope. */
    public static ApiResult from(Response response, String body) {
        int status = response.code();
        int retryAfter = parseInt(response.header("Retry-After"), 0);
        try {
            JSONObject root = new JSONObject(body);
            boolean ok = root.optBoolean("ok", response.isSuccessful());
            if (ok) {
                return new ApiResult(true, status, root.optJSONObject("data"), null, null, retryAfter);
            }
            return new ApiResult(false, status, null,
                    root.optString("code", "UNKNOWN"),
                    root.optString("message", "Request failed"),
                    retryAfter);
        } catch (Exception e) {
            // Body wasn't the expected JSON envelope. Treat as an error even on 2xx — a non-JSON
            // "success" means the backend contract broke, don't mask it as an empty ok.
            return new ApiResult(false, status, null,
                    response.isSuccessful() ? "BAD_RESPONSE" : "HTTP_" + status,
                    "Unexpected response format (" + status + ")",
                    retryAfter);
        }
    }

    /** Transport-level failure (no HTTP response at all). */
    public static ApiResult networkError(String message) {
        return new ApiResult(false, 0, null, "NETWORK_ERROR", message, 0);
    }

    /** Client-side failure before the request left the device (e.g. request serialization). */
    public static ApiResult clientError(String message) {
        return new ApiResult(false, 0, null, "CLIENT_ERROR", message, 0);
    }

    private static int parseInt(String s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
