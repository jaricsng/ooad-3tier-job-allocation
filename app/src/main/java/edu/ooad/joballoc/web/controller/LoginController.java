package edu.ooad.joballoc.web.controller;

import edu.ooad.joballoc.business.security.Actor;
import edu.ooad.joballoc.business.security.AuthService;
import edu.ooad.joballoc.web.http.FrontController;
import edu.ooad.joballoc.web.http.HttpStatus;
import edu.ooad.joballoc.web.http.Request;
import edu.ooad.joballoc.web.http.Response;
import edu.ooad.joballoc.web.http.Session;
import edu.ooad.joballoc.web.http.SessionManager;
import edu.ooad.joballoc.web.view.LoginView;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/** Sign-in and sign-out. */
public class LoginController {

    private static final Logger LOG = Logger.getLogger(LoginController.class.getName());

    private final AuthService auth;
    private final SessionManager sessions;
    private final List<LoginView.SsoButton> ssoButtons;

    public LoginController(AuthService auth, SessionManager sessions) {
        this(auth, sessions, List.of());
    }

    public LoginController(AuthService auth, SessionManager sessions, List<LoginView.SsoButton> ssoButtons) {
        this.auth = auth;
        this.sessions = sessions;
        this.ssoButtons = List.copyOf(ssoButtons);
    }

    public Response form(Request req) {
        if (req.session().isPresent()) {
            return Response.redirect("/board");
        }
        String error = "failed".equals(req.param("sso"))
                ? "Single sign-on did not complete. Sign in with your password, or ask an administrator to link "
                        + "your email to an account."
                : null;
        return page(error, "", HttpStatus.OK);
    }

    public Response login(Request req) {
        String username = req.param("username");
        // Login CSRF (double-submit token): the form's token must equal the cookie set with the form.
        String loginToken = req.param(FrontController.CSRF_FIELD);
        if (!SessionManager.matchesToken(req.cookie(SessionManager.LOGIN_COOKIE), loginToken)) {
            LOG.warning("Sign-in refused: missing or mismatched login token");
            return page("Your sign-in page expired. Please try again.", username, HttpStatus.FORBIDDEN);
        }
        Optional<Actor> actor = auth.authenticate(username, req.param("password"));
        if (actor.isEmpty()) {
            LOG.warning(() -> "Failed sign-in for username length " + (username == null ? 0 : username.length()));
            return page("Invalid username or password.", username, HttpStatus.UNAUTHORIZED);
        }
        req.session().ifPresent(s -> sessions.invalidate(s.id()));
        Session s = sessions.create(actor.get());
        LOG.info(() -> "Signed in: " + actor.get().username());
        return Response.redirect("/board").header("Set-Cookie", sessions.cookieFor(s));
    }

    public Response logout(Request req) {
        req.session().ifPresent(s -> sessions.invalidate(s.id()));
        return Response.redirect("/login").header("Set-Cookie", sessions.expiredCookie());
    }

    /** The sign-in page with a fresh login token in the form and in its cookie. */
    private Response page(String error, String username, HttpStatus status) {
        String token = sessions.newLoginToken();
        return Response.html(LoginView.render(error, username, token, ssoButtons)).status(status)
                .header("Set-Cookie", sessions.loginCookie(token));
    }
}
