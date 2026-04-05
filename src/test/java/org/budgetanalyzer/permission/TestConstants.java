package org.budgetanalyzer.permission;

/** Test constants for reusable test data. */
public final class TestConstants {

  // User IDs
  public static final String TEST_USER_ID = "usr_test123";
  public static final String TEST_ADMIN_ID = "usr_admin456";

  // Role IDs
  public static final String ROLE_USER = "USER";
  public static final String ROLE_ADMIN = "ADMIN";

  // Permissions
  public static final String PERM_USERS_READ = "users:read";
  public static final String PERM_USERS_WRITE = "users:write";
  public static final String PERM_ROLES_READ = "roles:read";
  public static final String PERM_ROLES_WRITE = "roles:write";
  public static final String PERM_ROLES_DELETE = "roles:delete";
  public static final String PERM_AUDIT_READ = "audit:read";

  // Deactivation
  public static final String TEST_DEACTIVATED_BY = TEST_ADMIN_ID;

  // IDP test subjects (auth0 prefix reflects current provider; idp_sub is provider-agnostic)
  public static final String TEST_IDP_SUB = "auth0|test123";
  public static final String TEST_IDP_SUB_ADMIN = "auth0|admin456";
  public static final String TEST_EMAIL = "test@example.com";
  public static final String TEST_DISPLAY_NAME = "Test User";

  private TestConstants() {
    // Utility class
  }
}
