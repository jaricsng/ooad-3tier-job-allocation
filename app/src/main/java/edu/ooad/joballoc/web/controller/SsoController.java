package edu.ooad.joballoc.web.controller;

import edu.ooad.joballoc.business.security.Actor;
import edu.ooad.joballoc.business.security.AuthService;
import edu.ooad.joballoc.business.security.ExternalIdentity;
import edu.ooad.joballoc.web.http.HttpStatus;
import edu.ooad.joballoc.web.http.Request;
import edu.ooad.joballoc.web.http.Response;
import edu.ooad.joballoc.web.http.Session;
import edu.ooad.joballoc.web.http.SessionManager;
import edu.ooad.joballoc.web.sso.OidcProvider;
import edu.ooad.joballoc.web.sso.SsoService;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Single sign-on with Google, Microsoft, Apple or a generic OpenID Connect provider.
 * <pre>
 *   GET  /sso/{provider}            start: redirect to the provider, state bound by a cookie
 *   GET  /sso/{provider}/callback   result as query parameters (Google, Microsoft, generic)
 *   POST /sso/{provider}/callback   result as a form post (Apple, response_mode=form_post)
 * </pre>
 * On success the person gets an ordinary session, exactly as after a password sign-in.
 */
public class SsoController {

    /** Cookie that ties a sign-in flow to the browser that started it. */
    public static final String STATE_COOKIE = "JA_SSO";

    private static final Logger LOG = Logger.getLogger(SsoController.class.getName());
    private static final long STATE_COOKIE_SECONDS = 600;
    private static final String FAILED = "/login?sso=failed";

    private final SsoService sso;
    private final AuthService auth;
    private final SessionManager sessions;
    private final AuthService.Provisioning provisioning;
    private final boolean secureCookie;

    public SsoController(SsoService sso, AuthService auth, SessionManager sessions,
            AuthService.Provisioning provisioning, boolean secureCookie) {
        this.sso = sso;
        this.auth = auth;
        this.sessions = sessions;
        this.provisioning = provisioning;
        this.secureCookie = secureCookie;
    }

    public Response start(Request req) {
        Optional<OidcProvider> p = sso.provider(req.pathVar("provider"));
        if (p.isEmpty()) {
            return Response.text(HttpStatus.NOT_FOUND, "Unknown sign-in provider");
        }
        try {
            SsoService.Start start = sso.start(p.get());
            return Response.redirect(start.redirectUrl()).header("Set-Cookie", stateCookie(start.state()));
        } catch (SsoService.SsoException e) {
            LOG.warning(() -> "SSO start failed: " + e.getMessage());
            return Response.redirect(FAILED);
        }
    }

    public Response callback(Request req) {
        Optional<OidcProvider> p = sso.provider(req.pathVar("provider"));
        if (p.isEmpty()) {
            return Response.text(HttpStatus.NOT_FOUND, "Unknown sign-in provider");
        }
        try {
            ExternalIdentity id = sso.callback(p.get(), req.param("state"), req.cookie(STATE_COOKIE),
                    req.param("code"), req.param("error"));
            Optional<Actor> actor = auth.authenticateExternal(id, provisioning);
            if (actor.isEmpty()) {
                LOG.warning(() -> "SSO sign-in refused: no account for this "
                        + (id.emailVerified() ? "" : "UNVERIFIED ") + "email from " + id.provider());
                return Response.redirect(FAILED);
            }
            req.session().ifPresent(s -> sessions.invalidate(s.id()));
            Session s = sessions.create(actor.get());
            LOG.info(() -> "Signed in with " + id.provider() + ": " + actor.get().username());
            return Response.redirect("/board").header("Set-Cookie", sessions.cookieFor(s));
        } catch (SsoService.SsoException e) {
            LOG.warning(() -> "SSO callback rejected: " + e.getMessage());
            return Response.redirect(FAILED);
        }
    }

    /**
     * Apple returns with a cross-site POST, which SameSite=Lax cookies do not survive, so behind HTTPS the
     * cookie is SameSite=None; Secure. Without HTTPS (local development) Lax is used.
     */
    private String stateCookie(String state) {
        return STATE_COOKIE + "=" + state + "; Path=/sso; HttpOnly; Max-Age=" + STATE_COOKIE_SECONDS
                + (secureCookie ? "; SameSite=None; Secure" : "; SameSite=Lax");
    }
}
