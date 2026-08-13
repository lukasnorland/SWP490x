package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.funix.swp490x.mrs.repository.SongRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Reading covers over HTTP, against a local server rather than a vendor CDN.
 *
 * <p>The behaviour that matters is that nothing here is ever allowed to fail an
 * import: a cover that 404s, is not an image, or is too big leaves the song
 * without a wash and the run untouched.
 */
class CoverAmbienceServiceTest {

    private HttpServer server;
    private String baseUrl;
    private SongRepository songRepository;
    private CoverAmbienceWriter writer;
    private CatalogProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cover.png", exchange -> respond(exchange, 200, "image/png", png()));
        server.createContext("/missing", exchange ->
                respond(exchange, 404, "text/plain", "gone".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/page", exchange -> respond(exchange, 200, "text/html",
                "<html>not a cover</html>".getBytes(StandardCharsets.UTF_8)));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        songRepository = mock(SongRepository.class);
        writer = mock(CoverAmbienceWriter.class);
        properties = new CatalogProperties();
        properties.getCoverArt().setTimeout(Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void readsACoverIntoWashColours() {
        Optional<CoverAmbience> ambience = service().read(baseUrl + "/cover.png");

        assertThat(ambience).isPresent();
        assertThat(ambience.get().a()).startsWith("rgba(");
    }

    @Test
    void shrugsOffACoverThatIsGone() {
        assertThat(service().read(baseUrl + "/missing")).isEmpty();
    }

    @Test
    void shrugsOffAResponseThatIsNotAnImage() {
        assertThat(service().read(baseUrl + "/page")).isEmpty();
    }

    /** A cover url is third-party input, so it may only ever be fetched over HTTP. */
    @Test
    void refusesToReadAnythingButHttp() {
        assertThat(service().read("file:///etc/passwd")).isEmpty();
        assertThat(service().read("not a url")).isEmpty();
    }

    @Test
    void leavesACoverBiggerThanTheBudgetAlone() {
        properties.getCoverArt().setMaxBytes(16);

        assertThat(service().read(baseUrl + "/cover.png")).isEmpty();
    }

    /**
     * The attempt is recorded whether or not it produced colours, which is what
     * keeps an unreadable cover out of every later import's batch.
     */
    @Test
    @SuppressWarnings("unchecked")
    void recordsWhatEachCoverGaveIncludingNothing() {
        given(songRepository.findCoversNeedingAmbience(any())).willReturn(List.of(
                new Object[] {1L, baseUrl + "/cover.png"},
                new Object[] {2L, baseUrl + "/missing"}));

        int filled;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            filled = service().fillMissing(pool);
        }

        assertThat(filled).isEqualTo(1);

        ArgumentCaptor<List<CoverAmbienceWriter.Update>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(writer).write(captor.capture());
        List<CoverAmbienceWriter.Update> written = captor.getValue();

        assertThat(written).hasSize(2);
        assertThat(written).anySatisfy(update -> {
            assertThat(update.songId()).isEqualTo(1L);
            assertThat(update.a()).startsWith("rgba(");
            assertThat(update.sourceUrl()).endsWith("/cover.png");
        });
        assertThat(written).anySatisfy(update -> {
            assertThat(update.songId()).isEqualTo(2L);
            assertThat(update.a()).isNull();
            assertThat(update.b()).isNull();
            assertThat(update.sourceUrl()).endsWith("/missing");
        });
    }

    @Test
    void doesNothingAtAllWhenTurnedOff() {
        properties.getCoverArt().setEnabled(false);

        try (ExecutorService pool = Executors.newFixedThreadPool(1)) {
            assertThat(service().fillMissing(pool)).isZero();
        }
        verify(writer, org.mockito.Mockito.never()).write(any());
    }

    private CoverAmbienceService service() {
        return new CoverAmbienceService(songRepository, writer, properties);
    }

    private static void respond(HttpExchange exchange, int status, String type, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static byte[] png() {
        BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(180, 60, 40));
        graphics.fillRect(0, 0, 48, 48);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}
