package org.budgetanalyzer.permission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.budgetanalyzer.core.domain.SoftDeletableEntity;

/**
 * Represents a permission that can be granted to roles.
 *
 * <p>Permissions define specific actions that can be performed on resources. They follow the
 * pattern: resource_type:action (e.g., "transactions:read").
 */
@Entity
@Table(name = "permissions")
public class Permission extends SoftDeletableEntity {

  @Id
  @Column(name = "id", length = 100)
  private String id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "resource_type", nullable = false, length = 50)
  private String resourceType;

  @Column(name = "action", nullable = false, length = 50)
  private String action;

  public Permission() {}

  public Permission(
      String id, String name, String description, String resourceType, String action) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.resourceType = resourceType;
    this.action = action;
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

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }
}
