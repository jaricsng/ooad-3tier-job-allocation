package edu.ooad.joballoc.web.view;

import java.util.List;
import java.util.stream.Collectors;

/** BOUNDARY: the sign-in page (a centred Bootstrap card; full width on a phone). */
public final class LoginView {

    /** A "Sign in with ..." button for one enabled identity provider. */
    public record SsoButton(String providerId, String label) { }

    private LoginView() { }

    public static String render(String error, String username, String loginToken) {
        return render(error, username, loginToken, List.of());
    }

    public static String render(String error, String username, String loginToken, List<SsoButton> sso) {
        String buttons = sso.isEmpty() ? "" : "<div class=\"text-center text-body-secondary small my-3\">or</div>"
                + sso.stream().map(b -> "<a class=\"btn btn-outline-secondary w-100 mb-2\" href=\"/sso/"
                        + Html.esc(b.providerId()) + "\">Sign in with " + Html.esc(b.label()) + "</a>")
                        .collect(Collectors.joining());
        String alert = error == null ? "" : "<div class=\"alert alert-danger py-2\" role=\"alert\">" + Html.esc(error) + "</div>";
        String body = """
                <main class="container py-5">
                  <div class="row justify-content-center">
                    <div class="col-12 col-sm-10 col-md-7 col-lg-5 col-xl-4">
                      <div class="card shadow-sm border-0">
                        <div class="card-body p-4">
                          <h1 class="h4 mb-1 text-app">Sign in</h1>
                          <p class="text-body-secondary small mb-4">Allocate pending jobs to workers by skill and capacity.</p>
                          %s
                          <form method="post" action="/login">
                            <input type="hidden" name="_csrf" value="%s">
                            <div class="mb-3">
                              <label class="form-label" for="username">Username</label>
                              <input class="form-control" id="username" name="username" autocomplete="username" required maxlength="64" value="%s">
                            </div>
                            <div class="mb-4">
                              <label class="form-label" for="password">Password</label>
                              <input class="form-control" id="password" name="password" type="password" autocomplete="current-password" required maxlength="200">
                            </div>
                            <button type="submit" class="btn btn-primary w-100">Sign in</button>
                          </form>
                          %s
                          <p class="small text-body-secondary mt-4 mb-0">Demo accounts: <code>supervisor</code> (can allocate) and
                          <code>viewer</code> (read only). Passwords are in the user guide.</p>
                        </div>
                      </div>
                    </div>
                  </div>
                </main>
                """.formatted(alert, Html.esc(loginToken), Html.esc(username), buttons);
        return Html.page("Sign in", "", body);
    }
}
