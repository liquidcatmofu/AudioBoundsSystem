package io.github.liquidcatmofu.abs.ttsbridge;

/** Provider failures which Core may safely map to user-facing status and diagnostics. */
public final class TTSSynthesisException extends Exception {
    public enum Kind { UNAVAILABLE, TIMEOUT, HTTP_ERROR, INVALID_RESPONSE }

    private final Kind kind;
    private final int providerStatus;

    public TTSSynthesisException(Kind kind, String message, Throwable cause) {
        this(kind, -1, message, cause);
    }

    public TTSSynthesisException(Kind kind, int providerStatus, String message) {
        this(kind, providerStatus, message, null);
    }

    private TTSSynthesisException(Kind kind, int providerStatus, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.providerStatus = providerStatus;
    }

    public Kind kind() { return kind; }
    public int providerStatus() { return providerStatus; }
}
