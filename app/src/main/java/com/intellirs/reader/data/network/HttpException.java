package com.intellirs.reader.data.network;

/** Expected networking failure with a user-safe message and retry classification. */
public class HttpException extends Exception {
    public final int statusCode;
    public final boolean transientFailure;
    public final String safeMessage;

    public HttpException(String safeMessage, int statusCode, boolean transientFailure,
                         Throwable cause) {
        super(safeMessage, cause);
        this.safeMessage = safeMessage;
        this.statusCode = statusCode;
        this.transientFailure = transientFailure;
    }

    public HttpException(String safeMessage, int statusCode, boolean transientFailure) {
        this(safeMessage, statusCode, transientFailure, null);
    }

    public static HttpException network(String message) {
        return new HttpException(message, -1, true);
    }

    public static HttpException permanent(String message) {
        return new HttpException(message, -1, false);
    }

    public static HttpException http(int code, String message) {
        boolean retryable = code == 408 || code == 425 || code == 429 || code >= 500;
        return new HttpException(message, code, retryable);
    }
}
