package com.trackgo.service;

import com.trackgo.dto.CustomerSummary;
import com.trackgo.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final UserRepository users;

    public CustomerService(UserRepository users) {
        this.users = users;
    }

    /** There is no login, so the UI needs the list of customers to let you pick who you are. */
    @Transactional(readOnly = true)
    public List<CustomerSummary> list() {
        return users.findAll(Sort.by("id")).stream().map(CustomerSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public boolean exists(Long customerId) {
        return users.existsById(customerId);
    }
}
