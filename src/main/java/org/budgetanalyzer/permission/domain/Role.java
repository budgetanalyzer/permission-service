package org.budgetanalyzer.permission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.budgetanalyzer.core.domain.SoftDeletableEntity;

/**
 * Represents a role that can be assigned to users.
 *
 * <p>Roles support hierarchical inheritance through the parent_role_id field. A role inherits all
 * permissions from its parent role(s) in addition to its own.
 */
@Entity
@Table(name = "roles")
public class Role extends SoftDeletableEntity {

  @Id
  @Column(name = "id", length = 50)
  private String id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "parent_role_id", length = 50)
  private String parentRoleId;

  @Column(name = "is_system", nullable = false)
  private boolean system = false;

  public Role() {}

  public Role(String id, String name, String description, String parentRoleId) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.parentRoleId = parentRoleId;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getParentRoleId() {
    return parentRoleId;
  }

  public void setParentRoleId(String parentRoleId) {
    this.parentRoleId = parentRoleId;
  }

  public boolean isSystem() {
    return system;
  }

  public void setSystem(boolean system) {
    this.system = system;
  }
}
