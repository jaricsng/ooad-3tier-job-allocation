package edu.ooad.joballoc.web.sso;

import java.util.function.Supplier;

/**
 * One configured OpenID Connect identity provider.
 *
 * @param id               URL segment and log name: google, microsoft, apple or oidc
 * @param displayName      button label, for example "Google"
 * @param issuer           issuer URL; its /.well-known/openid-configuration is read on first use
 * @param clientId         the application's client id at the provider
 * @param clientSecret     how the client authenticates at the token endpoint (a fixed secret, or for
 *                         Apple a freshly signed JWT); null for public clients
 * @param scopes           requested scopes, always including openid and email
 * @param formPost         true if the provider posts the result back (Apple: response_mode=form_post)
 * @param pkce             send a PKCE S256 challenge (all except Apple, which does not document it)
 * @param trustedEmailClaim claim that marks the email as verified ("email_verified"; Microsoft: "xms_edov")
 */
public record OidcProvider(String id, String displayName, String issuer, String clientId,
        Supplier<String> clientSecret, String scopes, boolean formPost, boolean pkce, String trustedEmailClaim) { }
