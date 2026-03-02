package org.budgetanalyzer.permission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.budgetanalyzer.core.domain.SoftDeletableEntity;

/**
 * Represents a user in the permission system.
 *
 * <p>Users are linked to an identity provider via their {@code idp_sub} identifier and can be
 * assigned roles. The {@code idp_sub} field stores the OIDC {@code sub} claim from any compliant
 * provider (e.g., {@code "auth0|abc123"}, {@code "google-oauth2|123"}), making the architecture
 * provider-agnostic and avoiding identity provider lock-in.
 */
@Entity
@Table(name = "users")
public class User extends SoftDeletableEntity {

  @Id
  @Column(name = "id", length = 50)
  private String id;

  /** OIDC {@code sub} claim from the identity provider. Provider-agnostic. */
  @Column(name = "idp_sub", nullable = false, unique = true)
  private String idpSub;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "display_name")
  private String displayName;

  public User() {}

  public User(String id, String idpSub, String email, String displayName) {
    this.id = id;
    this.idpSub = idpSub;
    this.email = email;
    this.displayName = displayName;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getIdpSub() {
    return idpSub;
  }

  public void setIdpSub(String idpSub) {
    this.idpSub = idpSub;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }
}
