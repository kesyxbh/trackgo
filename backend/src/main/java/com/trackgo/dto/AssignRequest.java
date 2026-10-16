package com.trackgo.dto;

import jakarta.validation.constraints.NotNull;

public record AssignRequest(@NotNull Long riderId) {
}
