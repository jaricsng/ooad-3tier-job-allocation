package edu.ooad.joballoc.domain;

import edu.ooad.joballoc.persistence.mapping.Column;
import edu.ooad.joballoc.persistence.mapping.Entity;
import edu.ooad.joballoc.persistence.mapping.Id;
import edu.ooad.joballoc.persistence.mapping.Table;

import java.util.Locale;

/** A user who can sign in. Only a salted PBKDF2 hash of the password is stored. */
@Entity
@Table("app_user")
public class AppUser {

    @Id @Column("username") private String username;
    @Column("password_hash") private String passwordHash;
    @Column("role") private Role role;
    @Column("display_name") private String displayName;
    /** Verified email used to link single sign-on identities (lower case); null if the user never uses SSO. */
    @Column("email") private String email;

    protected AppUser() { }

    public AppUser(String username, String passwordHash, Role role, String displayName) {
        this(username, passwordHash, role, displayName, null);
    }

    public AppUser(String username, String passwordHash, Role role, String displayName, String email) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.displayName = displayName;
        this.email = email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
}
