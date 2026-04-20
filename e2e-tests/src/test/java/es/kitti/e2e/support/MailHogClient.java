package es.kitti.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailHogClient {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TOKEN_PATTERN = Pattern.compile("/activate\\?token=([\\w-]+)");

    /** Polls MailHog until an email arrives for the given address (max 15 s). */
    public static String waitForEmail(String toAddress) {
        AtomicReference<String> body = new AtomicReference<>();
        Awaitility.await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    String raw = fetchEmailBody(toAddress);
                    if (raw != null) {
                        body.set(raw);
                        return true;
                    }
                    return false;
                });
        return body.get();
    }

    public static String extractActivationToken(String emailBody) {
        // Remove Quoted-Printable soft line breaks and decode =3D
        String decodedBody = emailBody.replaceAll("=\\r?\\n", "").replace("=3D", "=");
        // Search in raw body and also in URL-decoded form (just in case)
        Matcher m = TOKEN_PATTERN.matcher(decodedBody);
        if (m.find()) {
            return m.group(1);
        }
        throw new AssertionError("Activation token not found in email body");
    }

    public static void deleteAll() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(E2EConfig.MAILHOG_URL + "/api/v1/messages"))
                    .DELETE()
                    .build();
            HTTP.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            // best-effort cleanup
        }
    }

    private static String fetchEmailBody(String toAddress) {
        try {
            String encoded = URLEncoder.encode(toAddress, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(E2EConfig.MAILHOG_URL + "/api/v2/search?kind=to&query=" + encoded))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = MAPPER.readTree(resp.body());
            if (root.path("total").asInt() == 0) return null;

            JsonNode item = root.path("items").get(0);
            // Try plain body first
            String plain = item.path("Content").path("Body").asText(null);
            if (plain != null && !plain.isBlank()) return plain;

            // Multipart: search MIME parts
            JsonNode parts = item.path("MIME").path("Parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    String partBody = part.path("Body").asText(null);
                    if (partBody != null && !partBody.isBlank()) return partBody;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
