package org.budgetanalyzer.permission.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.budgetanalyzer.permission.domain.UserRole;

/** Repository for UserRole entities (simple user-role join table). */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

  List<UserRole> findByUserId(String userId);

  List<UserRole> findByUserIdIn(Collection<String> userIds);

  Optional<UserRole> findByUserIdAndRoleId(String userId, String roleId);

  void deleteByUserIdAndRoleId(String userId, String roleId);

  int deleteByUserId(String userId);

  void deleteByRoleId(String roleId);

  @Query(
      "SELECT rp.permissionId FROM UserRole ur "
          + "JOIN RolePermission rp ON ur.roleId = rp.roleId "
          + "WHERE ur.userId = :userId")
  Set<String> findPermissionIdsByUserId(@Param("userId") String userId);

  @Query("SELECT ur.roleId FROM UserRole ur WHERE ur.userId = :userId")
  Set<String> findRoleIdsByUserId(@Param("userId") String userId);
}
