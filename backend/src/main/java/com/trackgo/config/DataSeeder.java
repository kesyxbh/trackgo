package com.trackgo.config;

import com.trackgo.entity.Rider;
import com.trackgo.entity.RiderStatus;
import com.trackgo.entity.User;
import com.trackgo.repository.RiderRepository;
import com.trackgo.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Inserts demo customers and riders on startup so the API and the UI can be tried right away
 * (there are no create-customer / create-rider endpoints). Does nothing if data exists.
 * Turn off with {@code trackgo.seed.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "trackgo.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final RiderRepository riders;
    private final Clock clock;

    public DataSeeder(UserRepository users, RiderRepository riders, Clock clock) {
        this.users = users;
        this.riders = riders;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0 || riders.count() > 0) {
            return;
        }
        Instant now = clock.instant();
        users.saveAll(List.of(
                new User("alice", "alice@example.com"),
                new User("bob", "bob@example.com")));
        // Riders start at real central-Beijing landmarks so the map and distances look plausible.
        // Rider C is OFFLINE to demonstrate that an offline rider is never assigned.
        riders.saveAll(List.of(
                new Rider("Rider A", RiderStatus.IDLE, 39.9087, 116.3975, now),     // Tiananmen Square
                new Rider("Rider B", RiderStatus.IDLE, 39.9143, 116.4107, now),     // Wangfujing
                new Rider("Rider C", RiderStatus.OFFLINE, 39.9025, 116.4272, now),  // Beijing Railway Station
                new Rider("Rider D", RiderStatus.IDLE, 39.9836, 116.3105, now)));   // Zhongguancun
        log.info("Seeded demo data: 2 customers (alice, bob) and 4 riders (A, B, D idle; C offline)");
    }
}
