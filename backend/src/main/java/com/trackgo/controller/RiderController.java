package com.trackgo.controller;

import com.trackgo.dto.LocationUpdateRequest;
import com.trackgo.dto.RiderResponse;
import com.trackgo.entity.RiderStatus;
import com.trackgo.service.RiderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/riders")
public class RiderController {

    private final RiderService service;

    public RiderController(RiderService service) {
        this.service = service;
    }

    /** Lets the dispatcher pick a rider, e.g. ?status=IDLE. */
    @GetMapping
    public List<RiderResponse> list(@RequestParam(required = false) RiderStatus status) {
        return service.list(status);
    }

    /** A rider reports their current position. */
    @PatchMapping("/{id}/location")
    public RiderResponse updateLocation(@PathVariable Long id, @Valid @RequestBody LocationUpdateRequest request) {
        return service.updateLocation(id, request);
    }
}
