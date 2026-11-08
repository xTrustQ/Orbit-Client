package dev.orbit.client.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.orbit.client.OrbitClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to the Orbit launcher over a private local connection (address + secret passed with
 * -Dorbit.bridge). Used by the title-screen Accounts and Locker tabs. Everything is async.
 */
public final class OrbitBridge {
    private OrbitBridge() {}
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final Gson GSON = new Gson();

    public static boolean available() { return base() != null; }
    private static String base() {
        String b = System.getProperty("orbit.bridge");
        return b == null || b.isBlank() ? null : b.endsWith("/") ? b : b + "/";
    }

    public static CompletableFuture<JsonElement> get(String path) { return call(path, null, 40); }
    public static CompletableFuture<JsonElement> post(String path, Object body) { return call(path, body == null ? new JsonObject() : body, 40); }
    /** POST with a longer wait (e.g. Microsoft sign-in, which waits for the user). */
    public static CompletableFuture<JsonElement> post(String path, Object body, int timeoutSeconds) { return call(path, body == null ? new JsonObject() : body, timeoutSeconds); }

    private static CompletableFuture<JsonElement> call(String path, Object body, int timeoutSeconds) {
        String b = base();
        if (b == null) return CompletableFuture.failedFuture(new IllegalStateException("Start Minecraft from the Orbit launcher to use this"));
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(b + path)).timeout(Duration.ofSeconds(timeoutSeconds));
        if (body != null) rb.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(res -> {
            JsonElement json = res.body().isBlank() ? new JsonObject() : JsonParser.parseString(res.body());
            if (res.statusCode() != 200) {
                String msg = json.isJsonObject() && json.getAsJsonObject().has("error") ? json.getAsJsonObject().get("error").getAsString() : "Launcher error " + res.statusCode();
                throw new IllegalStateException(msg);
            }
            return json;
        }).whenComplete((r, e) -> { if (e != null) OrbitClient.LOG.debug("Bridge {} failed", path, e); });
    }

    public static JsonObject obj(String k, String v) { JsonObject o = new JsonObject(); o.addProperty(k, v); return o; }
    public static JsonArray arr(JsonElement e) { return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray(); }
    /** Message of an exception from a future, without the wrapper class names. */
    public static String message(Throwable t) {
        while (t.getCause() != null && (t instanceof java.util.concurrent.CompletionException || t.getMessage() == null)) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
