package edu.ooad.joballoc.business.security;

/**
 * A person as asserted by an external identity provider after its ID token was verified: which
 * provider, the provider's stable subject id, the email and whether the provider verified it.
 * Protocol details (OIDC, tokens, keys) stay in the presentation tier; the business tier only decides
 * which account, if any, this identity may use.
 */
public record ExternalIdentity(String provider, String subject, String email, boolean emailVerified, String name) { }
