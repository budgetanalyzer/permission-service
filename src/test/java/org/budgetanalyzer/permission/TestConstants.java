package org.budgetanalyzer.permission;

/**
 * Test constants for reusable test data.
 *
 * <p>Centralizes common test values to ensure consistency across tests.
 */
public final class TestConstants {

  // User IDs
  public static final String TEST_USER_ID = "usr_test123";
  public static final String TEST_ADMIN_ID = "usr_admin456";
  public static final String TEST_MANAGER_ID = "usr_manager789";
  public static final String TEST_DELEGATEE_ID = "usr_delegatee321";

  // Role IDs
  public static final String ROLE_USER = "USER";
  public static final String ROLE_ADMIN = "SYSTEM_ADMIN";
  public static final String ROLE_ORG_ADMIN = "ORG_ADMIN";
  public static final String ROLE_MANAGER = "MANAGER";
  public static final String ROLE_ACCOUNTANT = "ACCOUNTANT";
  public static final String ROLE_AUDITOR = "AUDITOR";

  // Permissions
  public static final String PERM_USERS_READ = "users:read";
  public static final String PERM_USERS_WRITE = "users:write";
  public static final String PERM_ROLES_READ = "roles:read";
  public static final String PERM_ROLES_WRITE = "roles:write";
  public static final String PERM_ROLES_DELETE = "roles:delete";
  public static final String PERM_ASSIGN_BASIC = "user-roles:assign-basic";
  public static final String PERM_ASSIGN_ELEVATED = "user-roles:assign-elevated";
  public static final String PERM_REVOKE = "user-roles:revoke";
  public static final String PERM_DELEGATIONS_WRITE = "delegations:write";
  public static final String PERM_AUDIT_READ = "audit:read";

  // Auth0 test subjects
  public static final String TEST_AUTH0_SUB = "auth0|test123";
  public static final String TEST_AUTH0_SUB_ADMIN = "auth0|admin456";
  public static final String TEST_EMAIL = "test@example.com";
  public static final String TEST_DISPLAY_NAME = "Test User";

  private TestConstants() {
    // Utility class
  }
}
