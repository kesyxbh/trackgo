package com.trackgo.dto;

import com.trackgo.entity.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull DeliveryStatus status) {
}
