package org.budgetanalyzer.permission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.budgetanalyzer.core.domain.AuditableEntity;

/** Represents a role assignment to a user. Simple join table entity. */
@Entity
@Table(name = "user_roles")
public class UserRole extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, length = 50)
  private String userId;

  @Column(name = "role_id", nullable = false, length = 50)
  private String roleId;

  public UserRole() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getRoleId() {
    return roleId;
  }

  public void setRoleId(String roleId) {
    this.roleId = roleId;
  }
}
