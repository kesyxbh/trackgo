package com.trackgo.entity;

import static com.trackgo.entity.DeliveryStatus.ASSIGNED;
import static com.trackgo.entity.DeliveryStatus.DELIVERED;
import static com.trackgo.entity.DeliveryStatus.IN_TRANSIT;
import static com.trackgo.entity.DeliveryStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The state machine is a pure function of two enum values, so it can be tested exhaustively. */
class DeliveryStatusTest {

    @Test
    void onlyTheFourForwardMovesAreLegal() {
        // For every status, the complete set of legal next statuses.
        Map<DeliveryStatus, Set<DeliveryStatus>> legal = Map.of(
                PENDING, EnumSet.of(ASSIGNED),
                ASSIGNED, EnumSet.of(IN_TRANSIT),
                IN_TRANSIT, EnumSet.of(DELIVERED),
                DELIVERED, EnumSet.noneOf(DeliveryStatus.class));

        for (DeliveryStatus from : DeliveryStatus.values()) {
            for (DeliveryStatus to : DeliveryStatus.values()) {
                boolean expected = legal.get(from).contains(to);
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void noStatusCanTransitionToItself() {
        for (DeliveryStatus status : DeliveryStatus.values()) {
            assertThat(status.canTransitionTo(status)).as("%s -> %s", status, status).isFalse();
        }
    }
}
