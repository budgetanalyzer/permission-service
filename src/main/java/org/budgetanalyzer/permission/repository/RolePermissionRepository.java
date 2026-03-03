package org.budgetanalyzer.permission.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.budgetanalyzer.permission.domain.RolePermission;

/** Repository for RolePermission entities (simple role-permission join table). */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

  List<RolePermission> findByRoleId(String roleId);

  void deleteByRoleId(String roleId);
}
