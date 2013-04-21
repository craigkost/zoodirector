package com.kostbot.zoodirector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Craig Kost
 */
public class ConditionRetry {
    private static final Logger logger = LoggerFactory.getLogger(ConditionRetry.class);

    public static final int DEFAULT_MAX_ATTEMPTS = 9; // Attempt up to 2 seconds.
    public static final long DEFAULT_RETRY_INTERVAL = 250L;

    public static interface Condition {
        public boolean check();
    }

    /**
     * Check until the condition is true or we run out of attempts.
     *
     * @param condition condition to be checked
     * @return true if condition is met within retry attempts, false otherwise
     */
    public static boolean checkCondition(Condition condition) {
        return checkCondition(DEFAULT_MAX_ATTEMPTS, DEFAULT_RETRY_INTERVAL, condition);
    }

    /**
     * Check until the condition is true or we run out of attempts.
     *
     * @param maxAttempts   number of attempts to try
     * @param retryInterval retry interval in milliseconds
     * @param condition     condition to be checked
     * @return true if condition is met within retry attempts, false otherwise
     */
    public static boolean checkCondition(int maxAttempts, long retryInterval, Condition condition) {

        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            logger.debug("attempt {}", attempt);

            if (condition.check()) {
                return true;
            }

            // Only sleep if we haven't run out of attempts.
            if (attempt < maxAttempts) {
                try {
                    logger.debug("retry condition in {}ms", retryInterval);
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        return false;
    }
}
