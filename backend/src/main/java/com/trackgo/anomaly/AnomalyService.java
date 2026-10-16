package com.trackgo.anomaly;

import com.trackgo.config.AnomalyProperties;
import com.trackgo.dto.AnomalyResponse;
import com.trackgo.entity.Delivery;
import com.trackgo.entity.DeliveryStatus;
import com.trackgo.entity.Rider;
import com.trackgo.exception.ResourceNotFoundException;
import com.trackgo.repository.DeliveryRepository;
import com.trackgo.service.EtaService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Detects a delivery whose rider has gone quiet.
 *
 * <p><b>The rule</b> (deliberately a rule, not AI): a delivery that is ASSIGNED or IN_TRANSIT is
 * "stalled" when its rider's last position report is older than {@code trackgo.anomaly.stale-seconds}.
 *
 * <p><b>The wording</b> is the only place a language model is involved, and only to make the
 * message friendlier. If no model is configured, or it fails, a fixed English sentence is used, so
 * detection never depends on the model.
 */
@Service
public class AnomalyService {

    public static final String TYPE_RIDER_STALLED = "RIDER_STALLED";

    /** An AI-written note together with the position report it was written for. */
    private record Notice(Instant lastLocationAt, String message) {
    }

    private final DeliveryRepository deliveries;
    private final EtaService eta;
    private final AnomalyProperties properties;
    private final Clock clock;
    private final List<AnomalyExplainer> explainers;

    /**
     * Cache of AI-written notes, one per delivery. Without it every 3-second poll of the tracking
     * page would call the language model again (slow and costly). A note stays valid until the
     * rider reports a new position, which is exactly when the situation has changed.
     */
    private final Map<Long, Notice> notices = new ConcurrentHashMap<>();

    public AnomalyService(DeliveryRepository deliveries, EtaService eta, AnomalyProperties properties,
                          Clock clock, List<AnomalyExplainer> explainers) {
        this.deliveries = deliveries;
        this.eta = eta;
        this.properties = properties;
        this.clock = clock;
        this.explainers = explainers;
    }

    public AnomalyResponse check(Long deliveryId) {
        Delivery delivery = deliveries.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery " + deliveryId + " not found"));
        Rider rider = delivery.getRider();

        boolean active = delivery.getStatus() == DeliveryStatus.ASSIGNED
                || delivery.getStatus() == DeliveryStatus.IN_TRANSIT;
        if (!active || rider == null || rider.getLocationUpdatedAt() == null) {
            notices.remove(deliveryId);
            return AnomalyResponse.none();
        }

        Instant lastReport = rider.getLocationUpdatedAt();
        long staleSeconds = Math.max(0, Duration.between(lastReport, clock.instant()).getSeconds());
        if (staleSeconds < properties.staleSeconds()) {
            notices.remove(deliveryId);
            return AnomalyResponse.none();
        }

        Notice cached = notices.get(deliveryId);
        if (cached != null && cached.lastLocationAt().equals(lastReport)) {
            return new AnomalyResponse(true, TYPE_RIDER_STALLED, staleSeconds, lastReport, cached.message(), true);
        }

        AnomalyFacts facts = new AnomalyFacts(deliveryId, delivery.getStatus(), rider.getName(), staleSeconds,
                eta.estimate(delivery).remainingKm(), delivery.getPickupAddr(), delivery.getDropoffAddr());
        Optional<String> aiMessage = explain(facts);
        if (aiMessage.isPresent()) {
            notices.put(deliveryId, new Notice(lastReport, aiMessage.get()));
            return new AnomalyResponse(true, TYPE_RIDER_STALLED, staleSeconds, lastReport, aiMessage.get(), true);
        }
        return new AnomalyResponse(true, TYPE_RIDER_STALLED, staleSeconds, lastReport, fallbackMessage(facts), false);
    }

    private Optional<String> explain(AnomalyFacts facts) {
        for (AnomalyExplainer explainer : explainers) {
            Optional<String> message = explainer.explain(facts);
            if (message.isPresent()) {
                return message;
            }
        }
        return Optional.empty();
    }

    /** What the customer sees when no language model is available. Always correct, never fancy. */
    static String fallbackMessage(AnomalyFacts facts) {
        return "Your rider " + facts.riderName() + " has not reported a new position for "
                + facts.staleSeconds() + " seconds. Your delivery is still active and we are keeping an eye on it.";
    }
}
