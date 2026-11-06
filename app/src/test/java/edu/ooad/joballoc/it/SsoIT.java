package edu.ooad.joballoc.it;

import edu.ooad.joballoc.App;
import edu.ooad.joballoc.web.sso.FakeIdentityProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Single sign-on end to end over real HTTP: the app (on the test storage) and a fake OpenID Connect
 * provider, following every redirect by hand so each security check can be attacked on its own.
 */
public class SsoIT {

    private static FakeIdentityProvider idp;
    private static App.Running app;
    private static String base;
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeClass
    public static void start() throws Exception {
        idp = new FakeIdentityProvider();
        TestDatabase.reset();
        int port; // SSO_BASE_URL must name the app's port, so reserve a free one first
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        app = App.start(TestDatabase.config(Map.of("APP_PORT", Integer.toString(port),
                "SSO_BASE_URL", "http://127.0.0.1:" + port, "SSO_OIDC_NAME", "Test IdP",
                "SSO_OIDC_ISSUER", idp.issuer(), "SSO_OIDC_CLIENT_ID", FakeIdentityProvider.CLIENT_ID,
                "SSO_OIDC_CLIENT_SECRET", FakeIdentityProvider.CLIENT_SECRET)), Clock.systemDefaultZone());
        base = "http://127.0.0.1:" + port;
    }

    @AfterClass
    public static void stop() {
        if (app != null) {
            app.stop();
        }
        if (idp != null) {
            idp.close();
        }
    }

    private static HttpResponse<String> get(String url, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET();
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String location(HttpResponse<String> r) {
        return r.headers().firstValue("Location").orElse("");
    }

    private static String cookie(HttpResponse<String> r, String name) {
        return r.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith(name + "="))
                .map(c -> c.split(";")[0]).findFirst().orElse(null);
    }

    /** start -> provider -> callback; returns the callback response. */
    private static HttpResponse<String> signIn(boolean sendStateCookie) throws Exception {
        HttpResponse<String> start = get(base + "/sso/oidc", null);
        assertTrue("redirect to the provider", start.statusCode() == 302 || start.statusCode() == 303);
        assertTrue(location(start).startsWith(idp.issuer() + "/authorize?"));
        assertTrue("PKCE challenge sent", location(start).contains("code_challenge_method=S256"));
        HttpResponse<String> back = get(location(start), null);
        return get(location(back), sendStateCookie ? cookie(start, "JA_SSO") : null);
    }

    @Test
    public void theSignInPageOffersTheProvider() throws Exception {
        assertTrue(get(base + "/login", null).body().contains("Sign in with Test IdP"));
    }

    @Test
    public void aVerifiedLinkedEmailGetsASession() throws Exception {
        idp.next().email("supervisor@example.com");
        HttpResponse<String> callback = signIn(true);
        assertEquals("/board", location(callback));
        String session = cookie(callback, "JA_SESSION");
        HttpResponse<String> board = get(base + "/board", session);
        assertEquals(200, board.statusCode());
        assertTrue(board.body().contains("Sam Supervisor"));
    }

    @Test
    public void anUnverifiedOrUnknownEmailIsRefused() throws Exception {
        idp.next().unverified();
        assertEquals("/login?sso=failed", location(signIn(true)));
        idp.next().email("stranger@example.com");
        assertEquals("/login?sso=failed", location(signIn(true)));
    }

    @Test
    public void aCallbackWithoutTheBrowsersStateCookieIsRefused() throws Exception {
        idp.next();
        HttpResponse<String> callback = signIn(false);
        assertEquals("login CSRF: state not bound to this browser", "/login?sso=failed", location(callback));
        assertEquals(null, cookie(callback, "JA_SESSION"));
    }

    @Test
    public void aReplayedCallbackIsRefused() throws Exception {
        idp.next();
        HttpResponse<String> start = get(base + "/sso/oidc", null);
        String callbackUrl = location(get(location(start), null));
        String stateCookie = cookie(start, "JA_SSO");
        assertEquals("/board", location(get(callbackUrl, stateCookie)));
        assertEquals("state is single use", "/login?sso=failed", location(get(callbackUrl, stateCookie)));
    }

    @Test
    public void aTokenFromAnotherKeyIsRefused() throws Exception {
        idp.next().signedWithOtherKey();
        assertEquals("/login?sso=failed", location(signIn(true)));
    }

    @Test
    public void unknownProvidersAre404() throws Exception {
        assertEquals(404, get(base + "/sso/facebook", null).statusCode());
    }
}
