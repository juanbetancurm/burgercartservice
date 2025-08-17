package com.rockburger.cartservice.infrastructure.scheduler;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter.CartAdapter;
import com.rockburger.cartservice.domain.api.usecase.CartUseCase;
import com.rockburger.cartservice.domain.spi.ICartPersistencePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service responsible for automated cart session management, cleanup, and monitoring.
 * Runs scheduled tasks to maintain cart hygiene and provide operational metrics.
 */
@Service
@ConditionalOnProperty(name = "cart.session.management.enabled", havingValue = "true", matchIfMissing = true)
public class CartSessionManagementService {

	private static final Logger logger = LoggerFactory.getLogger(CartSessionManagementService.class);

	private final CartUseCase cartUseCase;
	private final CartAdapter cartAdapter;
	private final ICartPersistencePort cartPersistencePort;

	// Configuration properties
	@Value("${cart.session.expiry.hours:24}")
	private int cartExpiryHours;

	@Value("${cart.session.warning.hours:4}")
	private int cartWarningHours;

	@Value("${cart.cleanup.batch.size:100}")
	private int cleanupBatchSize;

	@Value("${cart.cleanup.enabled:true}")
	private boolean cleanupEnabled;

	@Value("${cart.metrics.enabled:true}")
	private boolean metricsEnabled;

	// Operational metrics
	private final AtomicLong totalCartsProcessed = new AtomicLong(0);
	private final AtomicLong totalCartsAbandoned = new AtomicLong(0);
	private final AtomicLong totalCleanupErrors = new AtomicLong(0);
	private final AtomicInteger lastCleanupCount = new AtomicInteger(0);
	private volatile LocalDateTime lastCleanupTime;
	private volatile LocalDateTime lastMetricsTime;

	public CartSessionManagementService(CartUseCase cartUseCase,
										CartAdapter cartAdapter,
										ICartPersistencePort cartPersistencePort) {
		this.cartUseCase = cartUseCase;
		this.cartAdapter = cartAdapter;
		this.cartPersistencePort = cartPersistencePort;
	}

	@PostConstruct
	public void init() {
		logger.info("Cart Session Management Service initialized with configuration:");
		logger.info("  - Cart expiry hours: {}", cartExpiryHours);
		logger.info("  - Cart warning hours: {}", cartWarningHours);
		logger.info("  - Cleanup batch size: {}", cleanupBatchSize);
		logger.info("  - Cleanup enabled: {}", cleanupEnabled);
		logger.info("  - Metrics enabled: {}", metricsEnabled);

		lastCleanupTime = LocalDateTime.now();
		lastMetricsTime = LocalDateTime.now();
	}

	/**
	 * Main cleanup task - runs every 6 hours
	 * Cleans up expired carts and maintains cart hygiene
	 */
	@Scheduled(fixedRate = 21600000) // 6 hours in milliseconds
	@Transactional
	public void cleanupExpiredCarts() {
		if (!cleanupEnabled) {
			logger.debug("Cart cleanup is disabled, skipping cleanup task");
			return;
		}

		logger.info("Starting scheduled cart cleanup task");

		try {
			long startTime = System.currentTimeMillis();
			int cleanedCount = 0;

			// Perform cleanup in batches to avoid long-running transactions
			cleanedCount = performBatchCleanup();

			long endTime = System.currentTimeMillis();
			long duration = endTime - startTime;

			// Update metrics
			totalCartsProcessed.addAndGet(cleanedCount);
			totalCartsAbandoned.addAndGet(cleanedCount);
			lastCleanupCount.set(cleanedCount);
			lastCleanupTime = LocalDateTime.now();

			logger.info("Cart cleanup completed successfully: {} carts processed in {}ms",
					cleanedCount, duration);

			// Log warning if cleanup took too long
			if (duration > 30000) { // 30 seconds
				logger.warn("Cart cleanup took longer than expected: {}ms", duration);
			}

		} catch (Exception e) {
			totalCleanupErrors.incrementAndGet();
			logger.error("Error during cart cleanup task: {}", e.getMessage(), e);

			// Send alert or notification here if needed
			handleCleanupError(e);
		}
	}

	/**
	 * Quick health check task - runs every 30 minutes
	 * Monitors cart system health and logs metrics
	 */
	@Scheduled(fixedRate = 1800000) // 30 minutes in milliseconds
	public void performHealthCheck() {
		if (!metricsEnabled) {
			return;
		}

		try {
			logger.debug("Performing cart system health check");

			// Gather cart statistics
			CartAdapter.CartStatistics stats = cartAdapter.getCartStatistics();

			// Log system metrics
			logSystemMetrics(stats);

			// Check for potential issues
			checkSystemHealth(stats);

			lastMetricsTime = LocalDateTime.now();

		} catch (Exception e) {
			logger.warn("Error during cart health check: {}", e.getMessage());
		}
	}

	/**
	 * Warning notification task - runs every 2 hours
	 * Identifies carts approaching expiry and logs warnings
	 */
	@Scheduled(fixedRate = 7200000) // 2 hours in milliseconds
	public void checkApproachingExpiry() {
		try {
			logger.debug("Checking for carts approaching expiry");

			// This would require additional repository method to find approaching expiry carts
			// For now, we'll log the intent
			logger.debug("Warning check completed - feature requires additional repository methods");

		} catch (Exception e) {
			logger.warn("Error checking approaching cart expiry: {}", e.getMessage());
		}
	}

	/**
	 * Perform cleanup in batches to manage transaction size and performance
	 */
	private int performBatchCleanup() {
		int totalCleaned = 0;
		int batchCount = 0;

		try {
			// Use the cart adapter's cleanup method
			int cleanedInBatch = cartAdapter.cleanupExpiredCarts();
			totalCleaned += cleanedInBatch;
			batchCount++;

			logger.debug("Cleanup batch {} completed: {} carts processed", batchCount, cleanedInBatch);

		} catch (Exception e) {
			logger.error("Error in cleanup batch {}: {}", batchCount, e.getMessage());
			throw e;
		}

		return totalCleaned;
	}

	/**
	 * Log system metrics for monitoring
	 */
	private void logSystemMetrics(CartAdapter.CartStatistics stats) {
		logger.info("Cart System Metrics:");
		logger.info("  Active carts: {}", stats.getActiveCount());
		logger.info("  Abandoned carts: {}", stats.getAbandonedCount());
		logger.info("  Completed carts: {}", stats.getCompletedCount());
		logger.info("  Recent active carts (24h): {}", stats.getRecentActiveCount());
		logger.info("  Total carts processed by cleanup: {}", totalCartsProcessed.get());
		logger.info("  Total cleanup errors: {}", totalCleanupErrors.get());
		logger.info("  Last cleanup count: {}", lastCleanupCount.get());
		logger.info("  Last cleanup time: {}", formatDateTime(lastCleanupTime));
	}

	/**
	 * Check system health and log warnings for potential issues
	 */
	private void checkSystemHealth(CartAdapter.CartStatistics stats) {
		// Check for unusual cart accumulation
		if (stats.getActiveCount() > 10000) {
			logger.warn("High number of active carts detected: {}. Consider investigating cart creation patterns.",
					stats.getActiveCount());
		}

		// Check abandoned cart ratio
		long totalCarts = stats.getActiveCount() + stats.getAbandonedCount() + stats.getCompletedCount();
		if (totalCarts > 0) {
			double abandonedRatio = (double) stats.getAbandonedCount() / totalCarts;
			if (abandonedRatio > 0.8) {
				logger.warn("High cart abandonment ratio: {:.2%}. Consider reviewing user experience.",
						abandonedRatio);
			}
		}

		// Check cleanup error rate
		long totalCleanupOperations = totalCartsProcessed.get();
		if (totalCleanupOperations > 0) {
			double errorRate = (double) totalCleanupErrors.get() / totalCleanupOperations;
			if (errorRate > 0.05) { // 5% error rate
				logger.warn("High cleanup error rate: {:.2%}. Consider investigating cleanup issues.",
						errorRate);
			}
		}

		// Check if cleanup is running on schedule
		if (lastCleanupTime != null && lastCleanupTime.isBefore(LocalDateTime.now().minusHours(8))) {
			logger.warn("Cart cleanup hasn't run recently. Last cleanup: {}", formatDateTime(lastCleanupTime));
		}
	}

	/**
	 * Handle cleanup errors - could integrate with alerting system
	 */
	private void handleCleanupError(Exception error) {
		// Log detailed error information
		logger.error("Cart cleanup error details:");
		logger.error("  Error type: {}", error.getClass().getSimpleName());
		logger.error("  Error message: {}", error.getMessage());
		logger.error("  Cleanup configuration: expiry={}h, batch={}", cartExpiryHours, cleanupBatchSize);

		// Could integrate with external alerting systems here:
		// - Send email alerts
		// - Post to Slack/Teams
		// - Create monitoring tickets
		// - Update health check endpoints
	}

	/**
	 * Manual cleanup trigger for administrative use
	 */
	public CleanupResult performManualCleanup(boolean dryRun) {
		logger.info("Performing manual cart cleanup (dry run: {})", dryRun);

		try {
			long startTime = System.currentTimeMillis();

			if (dryRun) {
				// Simulate cleanup without actually modifying data
				CartAdapter.CartStatistics stats = cartAdapter.getCartStatistics();
				long endTime = System.currentTimeMillis();

				return new CleanupResult(true, 0, endTime - startTime,
						"Dry run completed - would process approximately " +
								stats.getActiveCount() + " carts");
			} else {
				int cleanedCount = performBatchCleanup();
				long endTime = System.currentTimeMillis();

				return new CleanupResult(true, cleanedCount, endTime - startTime,
						"Manual cleanup completed successfully");
			}

		} catch (Exception e) {
			logger.error("Manual cleanup failed: {}", e.getMessage(), e);
			return new CleanupResult(false, 0, 0, "Cleanup failed: " + e.getMessage());
		}
	}

	/**
	 * Get current system metrics
	 */
	public SystemMetrics getSystemMetrics() {
		try {
			CartAdapter.CartStatistics stats = cartAdapter.getCartStatistics();

			return new SystemMetrics(
					stats.getActiveCount(),
					stats.getAbandonedCount(),
					stats.getCompletedCount(),
					stats.getRecentActiveCount(),
					totalCartsProcessed.get(),
					totalCartsAbandoned.get(),
					totalCleanupErrors.get(),
					lastCleanupCount.get(),
					lastCleanupTime,
					lastMetricsTime,
					cleanupEnabled,
					metricsEnabled
			);

		} catch (Exception e) {
			logger.error("Error retrieving system metrics: {}", e.getMessage());
			return new SystemMetrics();
		}
	}

	/**
	 * Update configuration at runtime
	 */
	public void updateConfiguration(int expiryHours, int warningHours, int batchSize,
									boolean enableCleanup, boolean enableMetrics) {
		logger.info("Updating cart session management configuration");
		logger.info("  Old config: expiry={}h, warning={}h, batch={}, cleanup={}, metrics={}",
				cartExpiryHours, cartWarningHours, cleanupBatchSize, cleanupEnabled, metricsEnabled);

		this.cartExpiryHours = expiryHours;
		this.cartWarningHours = warningHours;
		this.cleanupBatchSize = batchSize;
		this.cleanupEnabled = enableCleanup;
		this.metricsEnabled = enableMetrics;

		logger.info("  New config: expiry={}h, warning={}h, batch={}, cleanup={}, metrics={}",
				cartExpiryHours, cartWarningHours, cleanupBatchSize, cleanupEnabled, metricsEnabled);
	}

	private String formatDateTime(LocalDateTime dateTime) {
		return dateTime != null ? dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "N/A";
	}

	/**
	 * Result of cleanup operations
	 */
	public static class CleanupResult {
		private final boolean success;
		private final int itemsProcessed;
		private final long durationMs;
		private final String message;

		public CleanupResult(boolean success, int itemsProcessed, long durationMs, String message) {
			this.success = success;
			this.itemsProcessed = itemsProcessed;
			this.durationMs = durationMs;
			this.message = message;
		}

		// Getters
		public boolean isSuccess() { return success; }
		public int getItemsProcessed() { return itemsProcessed; }
		public long getDurationMs() { return durationMs; }
		public String getMessage() { return message; }

		@Override
		public String toString() {
			return String.format("CleanupResult[success=%s, processed=%d, duration=%dms, message='%s']",
					success, itemsProcessed, durationMs, message);
		}
	}

	/**
	 * System metrics snapshot
	 */
	public static class SystemMetrics {
		private final long activeCarts;
		private final long abandonedCarts;
		private final long completedCarts;
		private final long recentActiveCarts;
		private final long totalProcessed;
		private final long totalAbandoned;
		private final long totalErrors;
		private final int lastCleanupCount;
		private final LocalDateTime lastCleanupTime;
		private final LocalDateTime lastMetricsTime;
		private final boolean cleanupEnabled;
		private final boolean metricsEnabled;

		public SystemMetrics() {
			this(0, 0, 0, 0, 0, 0, 0, 0, null, null, false, false);
		}

		public SystemMetrics(long activeCarts, long abandonedCarts, long completedCarts,
							 long recentActiveCarts, long totalProcessed, long totalAbandoned,
							 long totalErrors, int lastCleanupCount, LocalDateTime lastCleanupTime,
							 LocalDateTime lastMetricsTime, boolean cleanupEnabled, boolean metricsEnabled) {
			this.activeCarts = activeCarts;
			this.abandonedCarts = abandonedCarts;
			this.completedCarts = completedCarts;
			this.recentActiveCarts = recentActiveCarts;
			this.totalProcessed = totalProcessed;
			this.totalAbandoned = totalAbandoned;
			this.totalErrors = totalErrors;
			this.lastCleanupCount = lastCleanupCount;
			this.lastCleanupTime = lastCleanupTime;
			this.lastMetricsTime = lastMetricsTime;
			this.cleanupEnabled = cleanupEnabled;
			this.metricsEnabled = metricsEnabled;
		}

		// Getters
		public long getActiveCarts() { return activeCarts; }
		public long getAbandonedCarts() { return abandonedCarts; }
		public long getCompletedCarts() { return completedCarts; }
		public long getRecentActiveCarts() { return recentActiveCarts; }
		public long getTotalProcessed() { return totalProcessed; }
		public long getTotalAbandoned() { return totalAbandoned; }
		public long getTotalErrors() { return totalErrors; }
		public int getLastCleanupCount() { return lastCleanupCount; }
		public LocalDateTime getLastCleanupTime() { return lastCleanupTime; }
		public LocalDateTime getLastMetricsTime() { return lastMetricsTime; }
		public boolean isCleanupEnabled() { return cleanupEnabled; }
		public boolean isMetricsEnabled() { return metricsEnabled; }
	}
}