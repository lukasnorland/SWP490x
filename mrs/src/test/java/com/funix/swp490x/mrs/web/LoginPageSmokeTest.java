package com.funix.swp490x.mrs.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Checks complete anonymous-page rendering in a real servlet container.
 * Unlike MockMvc, this exposes deferred-CSRF session creation after the response commits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginPageSmokeTest {

    @LocalServerPort
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/login?logout", "/login?error", "/login?expired", "/password-reset"})
    void anonymousScreensArriveComplete(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("name=\"email\"")
                .contains("<button type=\"submit\"")
                .contains("</html>");
    }
}
