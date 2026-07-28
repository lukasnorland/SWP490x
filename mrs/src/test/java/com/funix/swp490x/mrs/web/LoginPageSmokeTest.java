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
 * Renders the anonymous screens through a real servlet container.
 *
 * <p>MockMvc buffers the whole response, so a rendering failure that happens
 * after the container has already flushed its first 8 KB still looks like a pass
 * there: the assertions see the buffered prefix. Tomcat instead sends HTTP 200,
 * the partial page, and then drops the connection — the browser shows a page that
 * stops mid-card. Every screen below broke that way once CSRF token generation
 * ran late enough to need a session after the response had committed.
 *
 * <p>So these assertions are about completeness rather than markup: the form has
 * to survive all the way to {@code </html>}.
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
