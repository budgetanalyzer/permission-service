package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.event.PermissionChangeEvent;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionCacheService")
class PermissionCacheServiceTest {

  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private SetOperations<String, String> setOperations;

  private PermissionCacheService permissionCacheService;

  @BeforeEach
  void setUp() {
    permissionCacheService = new PermissionCacheService(redisTemplate, stringRedisTemplate);
  }

  @Nested
  @DisplayName("getCachedPermissions")
  class GetCachedPermissionsTests {

    @Test
    @DisplayName("should return cached permissions for user")
    void shouldReturnCachedPermissions() {
      // Arrange
      when(redisTemplate.opsForSet()).thenReturn(setOperations);
      var expectedKey = "permissions:" + TestConstants.TEST_USER_ID;
      var permissions = Set.of("transactions:read", "transactions:write");
      when(setOperations.members(expectedKey)).thenReturn(permissions);

      // Act
      var result = permissionCacheService.getCachedPermissions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result).containsExactlyInAnyOrder("transactions:read", "transactions:write");
    }

    @Test
    @DisplayName("should return null when no cached permissions")
    void shouldReturnNullWhenNoCachedPermissions() {
      // Arrange
      when(redisTemplate.opsForSet()).thenReturn(setOperations);
      var expectedKey = "permissions:" + TestConstants.TEST_USER_ID;
      when(setOperations.members(expectedKey)).thenReturn(null);

      // Act
      var result = permissionCacheService.getCachedPermissions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("cachePermissions")
  class CachePermissionsTests {

    @Test
    @DisplayName("should cache permissions with TTL")
    void shouldCachePermissionsWithTtl() {
      // Arrange
      when(redisTemplate.opsForSet()).thenReturn(setOperations);
      var permissions = Set.of("transactions:read", "transactions:write");
      var expectedKey = "permissions:" + TestConstants.TEST_USER_ID;

      // Act
      permissionCacheService.cachePermissions(TestConstants.TEST_USER_ID, permissions);

      // Assert
      verify(setOperations).add(eq(expectedKey), any(String[].class));
      verify(redisTemplate).expire(eq(expectedKey), any(Duration.class));
    }

    @Test
    @DisplayName("should not cache empty permissions")
    void shouldNotCacheEmptyPermissions() {
      // Arrange
      var permissions = Set.<String>of();

      // Act
      permissionCacheService.cachePermissions(TestConstants.TEST_USER_ID, permissions);

      // Assert - no interactions with set operations for add
      verify(redisTemplate, org.mockito.Mockito.never()).opsForSet();
    }
  }

  @Nested
  @DisplayName("invalidateCache")
  class InvalidateCacheTests {

    @Test
    @DisplayName("should delete cache key and publish invalidation event")
    void shouldDeleteCacheKeyAndPublishEvent() {
      // Arrange
      var expectedKey = "permissions:" + TestConstants.TEST_USER_ID;

      // Act
      permissionCacheService.invalidateCache(TestConstants.TEST_USER_ID);

      // Assert
      verify(redisTemplate).delete(expectedKey);
      verify(stringRedisTemplate)
          .convertAndSend("permission-invalidation", TestConstants.TEST_USER_ID);
    }
  }

  @Nested
  @DisplayName("onPermissionChange")
  class OnPermissionChangeTests {

    @Test
    @DisplayName("should invalidate cache on permission change event")
    void shouldInvalidateCacheOnPermissionChangeEvent() {
      // Arrange
      var event =
          PermissionChangeEvent.roleAssigned(
              TestConstants.TEST_USER_ID, "USER", TestConstants.TEST_ADMIN_ID);

      // Act
      permissionCacheService.onPermissionChange(event);

      // Assert
      verify(redisTemplate).delete("permissions:" + TestConstants.TEST_USER_ID);
    }
  }
}
