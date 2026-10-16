package com.trackgo.ai;

import com.trackgo.dto.DeliveryResponse;
import com.trackgo.dto.RiderResponse;
import com.trackgo.dto.TrackingResponse;
import com.trackgo.exception.ResourceNotFoundException;
import com.trackgo.service.DeliveryService;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The read-only "hands" of the delivery assistant. Each {@code @Tool} method is advertised to the
 * model with its name, description and parameter schema; the model asks for a call and the server
 * runs it.
 *
 * <p><b>Security design, in order of importance</b>
 * <ol>
 *   <li>The customer's identity is <em>not</em> a tool parameter. It travels in the
 *       {@link ToolContext}, which is never shown to the model, so the model cannot be talked into
 *       asking for somebody else's delivery.</li>
 *   <li>Ownership is enforced anyway, in {@link DeliveryService#getTrackingForCustomer}: the tool
 *       does not trust that the delivery id the model chose is one of the customer's.</li>
 *   <li>Every tool only reads. The worst a manipulated model can do is describe the customer's own
 *       deliveries wrongly; it cannot change or cancel anything.</li>
 *   <li>A per-request call budget ({@link ToolCallRecorder}) stops runaway tool loops.</li>
 * </ol>
 */
@Component
public class DeliveryTools {

    /** Key under which the caller's customer id is stored in the tool context. */
    public static final String CUSTOMER_ID = "customerId";
    /** Key under which the per-request {@link ToolCallRecorder} is stored in the tool context. */
    public static final String RECORDER = "recorder";

    private static final int LIST_LIMIT = 10;

    private final DeliveryService deliveries;
    private final Clock clock;

    public DeliveryTools(DeliveryService deliveries, Clock clock) {
        this.deliveries = deliveries;
        this.clock = clock;
    }

    // ---- result shapes (serialised to JSON for the model) ---------------------------------------

    public record DeliveryStatusView(Long deliveryId, String status, String pickupAddr, String dropoffAddr,
                                     String riderName, String riderStatus) {
    }

    public record RiderLocationView(Long deliveryId, String riderName, Double lat, Double lng,
                                    Long secondsSinceLastReport, String note) {
    }

    public record EtaView(Long deliveryId, String status, Double remainingKm, Double etaMinutes,
                          Double assumedSpeedKmh, String note) {
    }

    public record DeliverySummaryView(Long deliveryId, String status, String pickupAddr, String dropoffAddr) {
    }

    public record ToolError(String error) {
    }

    // ---- tools ----------------------------------------------------------------------------------

    @Tool(name = "getDeliveryStatus",
            description = "Get the current status of one of the customer's deliveries: PENDING (waiting for a rider), "
                    + "ASSIGNED (rider on the way to pick it up), IN_TRANSIT (on the way to the customer) or DELIVERED. "
                    + "Also returns the addresses and the assigned rider, if any.")
    public Object getDeliveryStatus(
            @ToolParam(description = "Numeric id of the delivery") Long deliveryId,
            ToolContext context) {
        return run(context, "getDeliveryStatus", argsOf("deliveryId", deliveryId), () -> {
            TrackingResponse tracking = deliveries.getTrackingForCustomer(customerId(context), deliveryId);
            DeliveryResponse d = tracking.delivery();
            RiderResponse rider = d.rider();
            return new DeliveryStatusView(d.id(), d.status().name(), d.pickupAddr(), d.dropoffAddr(),
                    rider == null ? null : rider.name(),
                    rider == null ? null : rider.status().name());
        });
    }

    @Tool(name = "getRiderLocation",
            description = "Get the last known position (latitude, longitude) of the rider assigned to one of the "
                    + "customer's deliveries and how many seconds ago it was reported.")
    public Object getRiderLocation(
            @ToolParam(description = "Numeric id of the delivery") Long deliveryId,
            ToolContext context) {
        return run(context, "getRiderLocation", argsOf("deliveryId", deliveryId), () -> {
            RiderResponse rider = deliveries.getTrackingForCustomer(customerId(context), deliveryId).delivery().rider();
            if (rider == null) {
                return new RiderLocationView(deliveryId, null, null, null, null,
                        "No rider has been assigned to this delivery yet.");
            }
            Long age = rider.locationUpdatedAt() == null ? null
                    : Math.max(0, Duration.between(rider.locationUpdatedAt(), clock.instant()).getSeconds());
            return new RiderLocationView(deliveryId, rider.name(), rider.lat(), rider.lng(), age, null);
        });
    }

    @Tool(name = "estimateEta",
            description = "Estimate how many minutes one of the customer's deliveries still needs, based on the "
                    + "rider's current position. The estimate is unknown until a rider is assigned.")
    public Object estimateEta(
            @ToolParam(description = "Numeric id of the delivery") Long deliveryId,
            ToolContext context) {
        return run(context, "estimateEta", argsOf("deliveryId", deliveryId), () -> {
            TrackingResponse t = deliveries.getTrackingForCustomer(customerId(context), deliveryId);
            // The speed is the simulator's speed, chosen so that a demo delivery finishes in a few minutes.
            // Say so in the tool result, otherwise the model reports "120 km/h" as if it were a real rider speed.
            String note = t.etaMinutes() == null
                    ? "The estimate is not available yet (no rider assigned)."
                    : "Demo speed: riders in this system are simulated at about " + Math.round(t.assumedSpeedKmh())
                    + " km/h, so this estimate is a demo figure, not a prediction for real traffic.";
            return new EtaView(deliveryId, t.delivery().status().name(), t.remainingKm(), t.etaMinutes(),
                    t.assumedSpeedKmh(), note);
        });
    }

    @Tool(name = "listMyDeliveries",
            description = "List the customer's most recent deliveries (newest first, at most 10) with their ids and "
                    + "statuses. Use this when the customer does not say which delivery they mean.")
    public Object listMyDeliveries(ToolContext context) {
        return run(context, "listMyDeliveries", Map.of(), () -> {
            List<DeliveryResponse> mine = deliveries.search(customerId(context), null, null);
            return mine.stream()
                    .limit(LIST_LIMIT)
                    .map(d -> new DeliverySummaryView(d.id(), d.status().name(), d.pickupAddr(), d.dropoffAddr()))
                    .toList();
        });
    }

    // ---- plumbing -------------------------------------------------------------------------------

    /**
     * Runs a tool body with budget checking and call recording. Business "not found" failures are
     * turned into a small error object that the model can read and relay; anything unexpected
     * propagates and fails the whole chat request.
     */
    private Object run(ToolContext context, String name, Map<String, Object> args, Supplier<Object> body) {
        ToolCallRecorder recorder = (ToolCallRecorder) context.getContext().get(RECORDER);
        if (!recorder.hasBudget()) {
            recorder.record(name, args, false);
            return new ToolError("Tool call limit reached for this request. Answer with what you already know.");
        }
        try {
            Object result = body.get();
            recorder.record(name, args, true);
            return result;
        } catch (ResourceNotFoundException notFound) {
            recorder.record(name, args, false);
            return new ToolError(notFound.getMessage());
        }
    }

    private static Long customerId(ToolContext context) {
        return (Long) context.getContext().get(CUSTOMER_ID);
    }

    private static Map<String, Object> argsOf(String key, Object value) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(key, value);
        return args;
    }
}
