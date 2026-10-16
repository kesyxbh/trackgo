package com.trackgo.service;

import com.trackgo.dto.LocationUpdateRequest;
import com.trackgo.dto.RiderResponse;
import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import com.trackgo.exception.ResourceNotFoundException;
import com.trackgo.repository.RiderRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiderService {

    private final RiderRepository riders;
    private final Clock clock;

    public RiderService(RiderRepository riders, Clock clock) {
        this.riders = riders;
        this.clock = clock;
    }

    /** Optionally filtered by status, e.g. IDLE to find riders that can take a delivery. */
    @Transactional(readOnly = true)
    public List<RiderResponse> list(RiderStatus status) {
        List<Rider> found = status == null ? riders.findAllByOrderByIdAsc() : riders.findByStatusOrderByIdAsc(status);
        return found.stream().map(RiderResponse::from).toList();
    }

    /**
     * Stores the rider's latest position together with the time it was reported. The timestamp is
     * what the stalled-rider check looks at.
     */
    @Transactional
    public RiderResponse updateLocation(Long riderId, LocationUpdateRequest request) {
        Rider rider = riders.findById(riderId)
                .orElseThrow(() -> new ResourceNotFoundException("Rider " + riderId + " not found"));
        rider.updateLocation(request.lat(), request.lng(), clock.instant());
        return RiderResponse.from(rider);
    }
}
