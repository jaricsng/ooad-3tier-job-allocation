package edu.ooad.joballoc.web.view;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

/** HTML helpers. Every value from data or users goes through {@link #esc(String)} (OWASP XSS prevention). */
public final class Html {

    private Html() { }

    /** Escapes text for HTML element content and quoted attribute values. */
    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length() + s.length() / 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#x27;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    /** Builds a URL with encoded query parameters (null/blank values are skipped). */
    public static String url(String path, Map<String, String> params) {
        StringJoiner q = new StringJoiner("&");
        params.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                q.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8));
            }
        });
        return q.length() == 0 ? path : path + "?" + q;
    }

    /**
     * Page skeleton shared by all pages: Bootstrap 5.3 (CSS only, served from /static, so the
     * Content-Security-Policy still allows no scripts and no third-party hosts), then app.css.
     * Responsive: the viewport meta tag plus Bootstrap's grid.
     */
    public static String page(String title, String userBar, String body) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s · Job Allocation</title>
                <link rel="stylesheet" href="/static/bootstrap.min.css">
                <link rel="stylesheet" href="/static/app.css">
                </head>
                <body class="bg-body-tertiary d-flex flex-column min-vh-100">
                <nav class="navbar app-navbar shadow-sm" aria-label="Application">
                  <div class="container-fluid flex-wrap gap-2">
                    <span class="navbar-brand fw-semibold text-white mb-0">Workload Job Allocation</span>
                    <div class="d-flex flex-wrap align-items-center gap-2">%s</div>
                  </div>
                </nav>
                %s
                <footer class="mt-auto py-3 text-center small text-body-secondary">Job Allocation · OOAD 3-tier reference implementation</footer>
                </body>
                </html>
                """.formatted(esc(title), userBar, body);
    }

    public static String hiddenCsrf(String token) {
        return "<input type=\"hidden\" name=\"_csrf\" value=\"" + esc(token) + "\">";
    }
}
