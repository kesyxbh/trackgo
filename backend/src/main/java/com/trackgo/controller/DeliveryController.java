package com.trackgo.controller;

import com.trackgo.anomaly.AnomalyService;
import com.trackgo.dispatch.DispatchService;
import com.trackgo.dto.AnomalyResponse;
import com.trackgo.dto.AssignRequest;
import com.trackgo.dto.DeliveryCreateRequest;
import com.trackgo.dto.DeliveryResponse;
import com.trackgo.dto.StatusUpdateRequest;
import com.trackgo.dto.TrackingResponse;
import com.trackgo.entity.DeliveryStatus;
import com.trackgo.service.DeliveryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService service;
    private final DispatchService dispatch;
    private final AnomalyService anomalies;

    public DeliveryController(DeliveryService service, DispatchService dispatch, AnomalyService anomalies) {
        this.service = service;
        this.dispatch = dispatch;
        this.anomalies = anomalies;
    }

    /** Customer places an order. */
    @PostMapping
    public ResponseEntity<DeliveryResponse> create(@Valid @RequestBody DeliveryCreateRequest request) {
        DeliveryResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/deliveries/" + created.id())).body(created);
    }

    /** Status + assigned rider + rider position. */
    @GetMapping("/{id}")
    public DeliveryResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    /** Same as above plus remaining distance and ETA. This is what the tracking page polls. */
    @GetMapping("/{id}/tracking")
    public TrackingResponse tracking(@PathVariable Long id) {
        return service.getTracking(id);
    }

    /** Is the rider of this delivery stalled? Cheap to poll; the AI-written note is cached server-side. */
    @GetMapping("/{id}/anomaly")
    public AnomalyResponse anomaly(@PathVariable Long id) {
        return anomalies.check(id);
    }

    /** Optional filters, e.g. ?status=PENDING for dispatch, ?riderId=1 for a rider's own orders. */
    @GetMapping
    public List<DeliveryResponse> list(@RequestParam(required = false) Long customerId,
                                       @RequestParam(required = false) Long riderId,
                                       @RequestParam(required = false) DeliveryStatus status) {
        return service.search(customerId, riderId, status);
    }

    /** Manual dispatch: assign an IDLE rider to a PENDING delivery. */
    @PostMapping("/{id}/assign")
    public DeliveryResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return service.assign(id, request.riderId());
    }

    /** Automatic dispatch: assign the nearest IDLE rider that has a known position. */
    @PostMapping("/{id}/auto-assign")
    public DeliveryResponse autoAssign(@PathVariable Long id) {
        return dispatch.autoAssign(id);
    }

    /** Rider progress: IN_TRANSIT (picked up) and DELIVERED. */
    @PatchMapping("/{id}/status")
    public DeliveryResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateStatus(id, request.status());
    }
}
