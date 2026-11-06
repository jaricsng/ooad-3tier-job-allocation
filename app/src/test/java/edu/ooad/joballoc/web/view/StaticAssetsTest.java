package edu.ooad.joballoc.web.view;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Supply-chain and CSP guards for the UI.
 * <ul>
 *   <li>The vendored Bootstrap CSS must match its pinned SHA-256 (no silent edits or swaps).</li>
 *   <li>Views must not emit inline {@code style=} or {@code <script>}: the CSP blocks both.</li>
 * </ul>
 */
public class StaticAssetsTest {

    /** SHA-256 of Bootstrap 5.3.8 dist/css/bootstrap.min.css (npm pack bootstrap@5.3.8). */
    private static final String BOOTSTRAP_SHA256 = "d85327d99c7a3ee1f9b5d0500d1370acea3ad2db39c163c2f51f232baedbdede";

    private static final Path VIEW_SOURCES = Path.of("src/main/java/edu/ooad/joballoc/web");

    @Test
    public void bootstrapCssMatchesPinnedHash() throws IOException, NoSuchAlgorithmException {
        try (InputStream in = StaticAssetsTest.class.getResourceAsStream("/static/bootstrap.min.css")) {
            assertNotNull("bootstrap.min.css is on the class path", in);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
            assertEquals(BOOTSTRAP_SHA256, HexFormat.of().formatHex(digest));
        }
    }

    @Test
    public void viewsEmitNoInlineStylesOrScripts() throws IOException {
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(VIEW_SOURCES)) {
            sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        for (Path source : sources) {
            String code = Files.readString(source);
            assertFalse(source + " emits an inline style attribute", code.contains(" style=\\\""));
            assertFalse(source + " emits a script tag", code.contains("<script"));
        }
    }
}
