package org.budgetanalyzer.permission.repository.spec;

import java.util.ArrayList;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import org.budgetanalyzer.permission.api.request.UserFilter;
import org.budgetanalyzer.permission.domain.User;

/** Specification builder for {@link User} queries based on {@link UserFilter}. */
public final class UserSpecifications {

  private static final char ESCAPE_CHAR = '\\';

  private UserSpecifications() {}

  /**
   * Builds a JPA {@link Specification} for filtering {@link User} entities using the provided
   * {@link UserFilter}.
   *
   * @param userFilter the user filter with user-specified criteria
   * @return a {@link Specification} to be used with Spring Data repositories
   */
  public static Specification<User> withFilter(UserFilter userFilter) {
    return (root, query, criteriaBuilder) -> {
      var predicates = new ArrayList<Predicate>();

      if (userFilter.id() != null && !userFilter.id().isBlank()) {
        predicates.add(criteriaBuilder.equal(root.get("id"), userFilter.id()));
      }

      if (userFilter.idpSub() != null && !userFilter.idpSub().isBlank()) {
        predicates.add(criteriaBuilder.equal(root.get("idpSub"), userFilter.idpSub()));
      }

      if (userFilter.status() != null) {
        predicates.add(criteriaBuilder.equal(root.get("status"), userFilter.status()));
      }

      var emailPredicate =
          createTextFilterPredicate(criteriaBuilder, root.get("email"), userFilter.email());
      if (emailPredicate != null) {
        predicates.add(emailPredicate);
      }

      var displayNamePredicate =
          createTextFilterPredicate(
              criteriaBuilder, root.get("displayName"), userFilter.displayName());
      if (displayNamePredicate != null) {
        predicates.add(displayNamePredicate);
      }

      if (userFilter.createdAfter() != null) {
        predicates.add(
            criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), userFilter.createdAfter()));
      }
      if (userFilter.createdBefore() != null) {
        predicates.add(
            criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), userFilter.createdBefore()));
      }

      if (userFilter.updatedAfter() != null) {
        predicates.add(
            criteriaBuilder.greaterThanOrEqualTo(root.get("updatedAt"), userFilter.updatedAfter()));
      }
      if (userFilter.updatedBefore() != null) {
        predicates.add(
            criteriaBuilder.lessThanOrEqualTo(root.get("updatedAt"), userFilter.updatedBefore()));
      }

      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static String escapeLikePattern(String input) {
    return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Predicate createTextFilterPredicate(
      CriteriaBuilder criteriaBuilder, Expression<String> fieldPath, String filterValue) {
    if (filterValue == null || filterValue.isBlank()) {
      return null;
    }

    var words = filterValue.trim().split("\\s+");
    var wordPredicates = new ArrayList<Predicate>();

    for (var word : words) {
      if (!word.isBlank()) {
        var escapedWord = escapeLikePattern(word.toLowerCase());
        wordPredicates.add(
            criteriaBuilder.like(
                criteriaBuilder.lower(fieldPath), "%" + escapedWord + "%", ESCAPE_CHAR));
      }
    }

    if (wordPredicates.isEmpty()) {
      return null;
    }

    return wordPredicates.size() == 1
        ? wordPredicates.get(0)
        : criteriaBuilder.or(wordPredicates.toArray(new Predicate[0]));
  }
}
