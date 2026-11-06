package edu.ooad.joballoc.web.sso;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FACTORY for the identity providers: presets for Google, Microsoft and Apple plus one generic OpenID
 * Connect provider (Okta, Keycloak, Entra ID with a fixed tenant, a test provider). A provider is
 * enabled only when its client id is set. Settings arrive as plain strings from the composition root,
 * because the web tier does not read configuration itself.
 */
public final class OidcProviders {

    private static final String EMAIL_VERIFIED = "email_verified";

    private OidcProviders() { }

    public static List<OidcProvider> fromSettings(Map<String, String> s) {
        List<OidcProvider> providers = new ArrayList<>();
        if (!blank(s.get("SSO_GOOGLE_CLIENT_ID"))) {
            providers.add(new OidcProvider("google", "Google", "https://accounts.google.com",
                    s.get("SSO_GOOGLE_CLIENT_ID"), fixed(s.get("SSO_GOOGLE_CLIENT_SECRET")), "openid email profile",
                    false, true, EMAIL_VERIFIED));
        }
        if (!blank(s.get("SSO_MICROSOFT_CLIENT_ID"))) {
            String tenant = blank(s.get("SSO_MICROSOFT_TENANT")) ? "organizations" : s.get("SSO_MICROSOFT_TENANT");
            // Microsoft does not send email_verified; the optional claim xms_edov ("email domain owner
            // verified") must be enabled in the app registration (see docs/sso.md).
            providers.add(new OidcProvider("microsoft", "Microsoft",
                    "https://login.microsoftonline.com/" + tenant + "/v2.0", s.get("SSO_MICROSOFT_CLIENT_ID"),
                    fixed(s.get("SSO_MICROSOFT_CLIENT_SECRET")), "openid email profile", false, true, "xms_edov"));
        }
        if (!blank(s.get("SSO_APPLE_CLIENT_ID"))) {
            AppleClientSecret secret = new AppleClientSecret(s.get("SSO_APPLE_TEAM_ID"), s.get("SSO_APPLE_CLIENT_ID"),
                    s.get("SSO_APPLE_KEY_ID"), Path.of(s.get("SSO_APPLE_PRIVATE_KEY_FILE")));
            providers.add(new OidcProvider("apple", "Apple", "https://appleid.apple.com", s.get("SSO_APPLE_CLIENT_ID"),
                    secret::create, "openid email name", true, false, EMAIL_VERIFIED));
        }
        if (!blank(s.get("SSO_OIDC_CLIENT_ID")) && !blank(s.get("SSO_OIDC_ISSUER"))) {
            String name = blank(s.get("SSO_OIDC_NAME")) ? "Single sign-on" : s.get("SSO_OIDC_NAME");
            providers.add(new OidcProvider("oidc", name, s.get("SSO_OIDC_ISSUER"), s.get("SSO_OIDC_CLIENT_ID"),
                    fixed(s.get("SSO_OIDC_CLIENT_SECRET")), "openid email profile", false, true, EMAIL_VERIFIED));
        }
        return List.copyOf(providers);
    }

    private static java.util.function.Supplier<String> fixed(String secret) {
        return () -> blank(secret) ? null : secret;
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }
}
