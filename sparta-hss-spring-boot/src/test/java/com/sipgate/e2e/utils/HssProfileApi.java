package com.sipgate.e2e.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Client for the HSS batch-profile HTTP API (BatchProfileResource). */
public final class HssProfileApi {

    private static final String BASE_URL = System.getProperty("e2e.hss.baseUrl", "http://hss:8080");

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static final ObjectMapper JSON = new ObjectMapper();

    private HssProfileApi() {}

    /** POSTs the complete desired {imsi -> profile-name} state and returns the HTTP status code. */
    public static int updateImsiProfiles(final Map<String, String> imsiToProfile) {
        try {
            final var request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/profile/imsi/batch"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(imsiToProfile)))
                .build();
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
        } catch (final Exception e) {
            throw new RuntimeException("batch profile update failed", e);
        }
    }
}
