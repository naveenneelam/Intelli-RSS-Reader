package com.intellirs.reader.data.network;

import androidx.annotation.Nullable;

import com.intellirs.reader.util.UrlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Bounded HTTP GET client. Certificate validation is left entirely to OkHttp/Android;
 * redirects are manual so loops, downgrade and redirect count can be enforced.
 */
public class HttpFetcher {
    public static final String USER_AGENT = "IntelliRSS/1.0 (Android; offline RSS reader)";
    private static final int MAX_REDIRECTS = 5;
    private final OkHttpClient client;

    public HttpFetcher() {
        client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .callTimeout(75, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(true)
                .build();
    }

    public HttpResult fetch(String url,
                            @Nullable String etag,
                            @Nullable String lastModified,
                            int maxBytes,
                            boolean allowHttp) throws HttpException {
        return fetch(url, etag, lastModified, maxBytes, allowHttp,
                "application/rss+xml, application/atom+xml, application/rdf+xml, " +
                        "application/xml, text/xml, text/html;q=0.9, */*;q=0.5");
    }

    public HttpResult fetch(String url,
                            @Nullable String etag,
                            @Nullable String lastModified,
                            int maxBytes,
                            boolean allowHttp,
                            String accept) throws HttpException {
        String current;
        try {
            current = UrlUtils.normalizeUserInput(url, allowHttp);
        } catch (IllegalArgumentException e) {
            throw HttpException.permanent(e.getMessage());
        }
        Set<String> visited = new HashSet<>();
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            if (!visited.add(UrlUtils.withoutFragment(current))) {
                throw HttpException.permanent("Redirect loop detected");
            }
            Request.Builder builder = new Request.Builder()
                    .url(current)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", accept)
                    .header("Accept-Language", "en, *;q=0.5");
            if (etag != null && !etag.isEmpty()) {
                builder.header("If-None-Match", etag);
            }
            if (lastModified != null && !lastModified.isEmpty()) {
                builder.header("If-Modified-Since", lastModified);
            }
            Call call = client.newCall(builder.build());
            try (Response response = call.execute()) {
                int code = response.code();
                if (code == 304) {
                    return new HttpResult(304, new byte[0],
                            response.header("Content-Type"), response.header("ETag"),
                            response.header("Last-Modified"), current, 0);
                }
                if (code >= 300 && code < 400) {
                    String location = response.header("Location");
                    if (location == null || location.isEmpty()) {
                        throw HttpException.http(code, "Redirect without a Location header");
                    }
                    String next = UrlUtils.resolve(current, location);
                    if (next == null) {
                        throw HttpException.permanent("Unsafe redirect destination");
                    }
                    try {
                        next = UrlUtils.normalizeUserInput(next, allowHttp);
                    } catch (IllegalArgumentException e) {
                        throw HttpException.permanent(e.getMessage());
                    }
                    current = next;
                    continue;
                }
                if (!response.isSuccessful()) {
                    throw HttpException.http(code, "Server returned HTTP " + code);
                }
                ResponseBody body = response.body();
                if (body == null) {
                    return new HttpResult(code, new byte[0], response.header("Content-Type"),
                            response.header("ETag"), response.header("Last-Modified"),
                            current, 0);
                }
                long contentLength = body.contentLength();
                if (contentLength > maxBytes) {
                    throw HttpException.permanent("Response is larger than the allowed limit");
                }
                byte[] bytes = readLimited(body.byteStream(), maxBytes);
                return new HttpResult(code, bytes, response.header("Content-Type"),
                        response.header("ETag"), response.header("Last-Modified"),
                        current, bytes.length);
            } catch (SSLException e) {
                throw HttpException.permanent("TLS certificate validation failed");
            } catch (SocketTimeoutException e) {
                throw HttpException.network("The server timed out");
            } catch (UnknownHostException e) {
                throw HttpException.network("The host could not be resolved");
            } catch (IOException e) {
                throw HttpException.network(safeIoMessage(e));
            }
        }
        throw HttpException.permanent("Too many redirects");
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException, HttpException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 32 * 1024));
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw HttpException.permanent("Response is larger than the allowed limit");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String safeIoMessage(IOException e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Network request failed";
        }
        return "Network request failed: " + message;
    }

    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
