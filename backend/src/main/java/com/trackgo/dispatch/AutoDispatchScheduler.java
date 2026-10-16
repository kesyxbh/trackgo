package com.trackgo.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background trigger for automatic dispatching. Every few seconds it hands out whatever pending
 * deliveries it can: new orders get a rider within one round, and orders that had to wait because
 * everybody was busy are picked up as soon as a rider becomes idle.
 *
 * <p>Why a polling loop and not "dispatch right when the order is created"? Doing it inside the
 * create request would either hold the customer's request open while we look for riders, or need an
 * after-commit event with its well-known transaction pitfalls. One small loop is simpler and also
 * covers the "waiting for a free rider" case for free.
 */
@Component
@ConditionalOnProperty(prefix = "trackgo.dispatch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AutoDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoDispatchScheduler.class);

    private final DispatchService dispatch;

    public AutoDispatchScheduler(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @Scheduled(fixedDelayString = "${trackgo.dispatch.retry-ms:3000}")
    public void dispatchPendingDeliveries() {
        try {
            int assigned = dispatch.dispatchPending();
            if (assigned > 0) {
                log.debug("Dispatch round assigned {} delivery(ies)", assigned);
            }
        } catch (RuntimeException e) {
            // A scheduled method that throws would be logged by Spring and rescheduled anyway;
            // catching here just keeps the log line meaningful.
            log.warn("Dispatch round failed", e);
        }
    }
}
