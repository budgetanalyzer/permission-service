package org.budgetanalyzer.permission.service;

import java.time.Duration;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.budgetanalyzer.permission.event.PermissionChangeEvent;

/**
 * Service for caching user permissions in Redis.
 *
 * <p>Provides cache management and invalidation for permission lookups.
 */
@Service
public class PermissionCacheService {

  private static final String PERMISSION_KEY_PREFIX = "permissions:";
  private static final String INVALIDATION_CHANNEL = "permission-invalidation";
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  private final RedisTemplate<String, String> redisTemplate;
  private final StringRedisTemplate stringRedisTemplate;

  /**
   * Constructs a new PermissionCacheService.
   *
   * @param redisTemplate the Redis template for permission caching
   * @param stringRedisTemplate the string Redis template for pub/sub
   */
  public PermissionCacheService(
      RedisTemplate<String, String> redisTemplate, StringRedisTemplate stringRedisTemplate) {
    this.redisTemplate = redisTemplate;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  /**
   * Gets cached permissions for a user.
   *
   * @param userId the user ID
   * @return set of cached permission IDs, or null if not cached
   */
  public Set<String> getCachedPermissions(String userId) {
    var key = PERMISSION_KEY_PREFIX + userId;
    return redisTemplate.opsForSet().members(key);
  }

  /**
   * Caches permissions for a user.
   *
   * @param userId the user ID
   * @param permissions the set of permission IDs to cache
   */
  public void cachePermissions(String userId, Set<String> permissions) {
    var key = PERMISSION_KEY_PREFIX + userId;
    if (!permissions.isEmpty()) {
      redisTemplate.opsForSet().add(key, permissions.toArray(new String[0]));
      redisTemplate.expire(key, CACHE_TTL);
    }
  }

  /**
   * Invalidates the cache for a user.
   *
   * @param userId the user ID
   */
  public void invalidateCache(String userId) {
    var key = PERMISSION_KEY_PREFIX + userId;
    redisTemplate.delete(key);

    // Publish invalidation event for other service instances
    stringRedisTemplate.convertAndSend(INVALIDATION_CHANNEL, userId);
  }

  /**
   * Event listener for permission changes to automatically invalidate cache.
   *
   * @param event the permission change event
   */
  @EventListener
  public void onPermissionChange(PermissionChangeEvent event) {
    invalidateCache(event.getUserId());
  }
}
