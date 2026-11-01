package com.intellirs.reader.data.ai;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellirs.reader.data.repository.SettingsRepository;
import com.intellirs.reader.util.UrlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Optional Ollama client. It is never called automatically: the user must enable local AI
 * and explicitly request an action in the reader. Article content is sent only to the
 * configured endpoint, which defaults to an emulator loopback address.
 */
public class LocalAiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final SettingsRepository settings;
    private final OkHttpClient client;

    public LocalAiClient(SettingsRepository settings) {
        this.settings = settings;
        client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .followRedirects(false)
                .build();
    }

    public boolean isEnabled() {
        return settings.localAiEnabled();
    }

    public void testConnection() throws LocalAiException {
        String base = normalizedBaseUrl();
        Request request = new Request.Builder()
                .url(base + "/api/tags")
                .header("Accept", "application/json")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new LocalAiException("Ollama returned HTTP " + response.code());
            }
        } catch (IOException e) {
            throw new LocalAiException(safe(e), e);
        }
    }

    public String summarize(String title, String text) throws LocalAiException {
        String prompt = "Summarize the following news article in three concise factual sentences. "
                + "Return plain text only, with no preamble.\n\nTitle: " + safeText(title)
                + "\n\nArticle:\n" + safeText(text);
        return generate(prompt);
    }

    public List<String> suggestTags(String title, String text) throws LocalAiException {
        String prompt = "Return 3 to 6 short topic tags for this article as a comma-separated list. "
                + "Use title case, no explanations, no hashtags.\n\nTitle: " + safeText(title)
                + "\n\nArticle:\n" + safeText(text);
        String response = generate(prompt);
        List<String> tags = new ArrayList<>();
        for (String part : response.replace("\n", ",").split(",")) {
            String tag = part.trim().replaceAll("^[#*\\-\\d.\\s]+", "").replaceAll("[*`]", "");
            if (!tag.isEmpty() && tag.length() <= 48
                    && !tags.contains(tag)) {
                tags.add(tag);
            }
            if (tags.size() >= 6) {
                break;
            }
        }
        return tags;
    }

    private String generate(String prompt) throws LocalAiException {
        if (!settings.localAiEnabled()) {
            throw new LocalAiException("Local AI is disabled");
        }
        String base = normalizedBaseUrl();
        JsonObject root = new JsonObject();
        root.addProperty("model", settings.getOllamaModel());
        root.addProperty("prompt", prompt);
        root.addProperty("stream", false);
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.2);
        root.add("options", options);
        Request request = new Request.Builder()
                .url(base + "/api/generate")
                .header("Accept", "application/json")
                .post(RequestBody.create(root.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new LocalAiException("Ollama returned HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new LocalAiException("Ollama returned an empty response");
            }
            long length = body.contentLength();
            if (length > 2_000_000L) {
                throw new LocalAiException("Ollama response was too large");
            }
            String json = readLimited(body.byteStream(), 2_000_000);
            JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
            if (!parsed.has("response")) {
                throw new LocalAiException("Ollama response did not contain text");
            }
            String text = parsed.get("response").getAsString().trim();
            if (text.isEmpty()) {
                throw new LocalAiException("Ollama returned no text");
            }
            return text;
        } catch (IOException | RuntimeException e) {
            throw new LocalAiException(safe(e), e);
        }
    }

    private String normalizedBaseUrl() throws LocalAiException {
        String raw = settings.getOllamaUrl();
        if (raw == null || raw.trim().isEmpty()) {
            throw new LocalAiException("Ollama URL is empty");
        }
        String normalized;
        try {
            normalized = UrlUtils.normalizeUserInput(raw, true);
        } catch (IllegalArgumentException e) {
            throw new LocalAiException(e.getMessage(), e);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String readLimited(InputStream input, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > max) {
                throw new IOException("Response is too large");
            }
            out.write(buffer, 0, read);
        }
        return out.toString("UTF-8");
    }

    private static String safeText(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.length() > 12_000 ? value.substring(0, 12_000) : value;
        return trimmed.replace("\u0000", "");
    }

    private static String safe(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Local AI request failed";
        }
        return "Local AI request failed: " + message;
    }
}
