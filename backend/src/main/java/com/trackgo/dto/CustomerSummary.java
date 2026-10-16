package com.trackgo.dto;

import com.trackgo.entity.User;

public record CustomerSummary(Long id, String username) {

    public static CustomerSummary from(User user) {
        return new CustomerSummary(user.getId(), user.getUsername());
    }
}
