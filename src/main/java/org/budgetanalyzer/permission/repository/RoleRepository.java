package org.budgetanalyzer.permission.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.budgetanalyzer.core.repository.SoftDeleteOperations;
import org.budgetanalyzer.permission.domain.Role;

/** Repository for Role entities with soft-delete support. */
@Repository
public interface RoleRepository extends JpaRepository<Role, String>, SoftDeleteOperations<Role> {

  Optional<Role> findByIdAndDeletedFalse(String id);

  List<Role> findAllByDeletedFalse();
}
