package org.budgetanalyzer.permission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.budgetanalyzer.core.domain.SoftDeletableEntity;

/**
 * Represents a user in the permission system.
 *
 * <p>Users are linked to Auth0 via their auth0_sub identifier and can be assigned roles, granted
 * resource permissions, and participate in delegations.
 */
@Entity
@Table(name = "users")
public class User extends SoftDeletableEntity {

  @Id
  @Column(name = "id", length = 50)
  private String id;

  @Column(name = "auth0_sub", nullable = false, unique = true)
  private String auth0Sub;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "display_name")
  private String displayName;

  public User() {}

  public User(String id, String auth0Sub, String email, String displayName) {
    this.id = id;
    this.auth0Sub = auth0Sub;
    this.email = email;
    this.displayName = displayName;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getAuth0Sub() {
    return auth0Sub;
  }

  public void setAuth0Sub(String auth0Sub) {
    this.auth0Sub = auth0Sub;
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
