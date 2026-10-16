package com.trackgo.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Pure unit tests: no Spring, no database. */
class GeoUtilsTest {

    private static final GeoPoint BEIJING = new GeoPoint(39.9042, 116.4074);
    private static final GeoPoint SHANGHAI = new GeoPoint(31.2304, 121.4737);

    @Test
    void haversine_beijingToShanghai_isAbout1068Km() {
        // The great-circle distance Beijing - Shanghai is commonly quoted as roughly 1,068 km.
        double km = GeoUtils.haversineKm(BEIJING, SHANGHAI);
        assertThat(km).isBetween(1060.0, 1075.0);
    }

    @Test
    void haversine_samePoint_isZero() {
        assertThat(GeoUtils.haversineKm(BEIJING, BEIJING)).isZero();
    }

    @Test
    void haversine_isSymmetric() {
        assertThat(GeoUtils.haversineKm(BEIJING, SHANGHAI))
                .isCloseTo(GeoUtils.haversineKm(SHANGHAI, BEIJING), within(1e-9));
    }

    @Test
    void haversine_oneDegreeOfLatitude_isAbout111Km() {
        double km = GeoUtils.haversineKm(0, 0, 1, 0);
        assertThat(km).isCloseTo(111.2, within(0.2));
    }

    @Test
    void moveToward_cityScale_advancesOneStepAlongTheLine() {
        // The scale the simulator really works at: a few kilometres, steps of a fraction of a kilometre.
        GeoPoint tiananmen = new GeoPoint(39.9087, 116.3975);
        GeoPoint cbd = new GeoPoint(39.9087, 116.4605);   // ~5.4 km east

        GeoPoint next = GeoUtils.moveToward(tiananmen, cbd, 1.0);

        assertThat(GeoUtils.haversineKm(tiananmen, next)).isCloseTo(1.0, within(0.001));
        assertThat(GeoUtils.haversineKm(next, cbd))
                .isCloseTo(GeoUtils.haversineKm(tiananmen, cbd) - 1.0, within(0.001));
    }

    @Test
    void moveToward_longDistance_headsTheRightWayButUndershootsSlightly() {
        // Interpolating latitude and longitude linearly is not a great circle, so over ~1,000 km the
        // step comes out about 1% short. That is irrelevant for a rider crossing a city, but worth
        // knowing, so the behaviour is pinned down here instead of being a surprise later.
        GeoPoint next = GeoUtils.moveToward(BEIJING, SHANGHAI, 10.0);

        double covered = GeoUtils.haversineKm(BEIJING, next);
        assertThat(covered).isBetween(9.8, 10.0);
        assertThat(GeoUtils.haversineKm(next, SHANGHAI)).isLessThan(GeoUtils.haversineKm(BEIJING, SHANGHAI));
    }

    @Test
    void moveToward_targetWithinOneStep_landsExactlyOnTarget() {
        GeoPoint near = new GeoPoint(BEIJING.lat() + 0.001, BEIJING.lng()); // ~111 m away

        assertThat(GeoUtils.moveToward(BEIJING, near, 5.0)).isEqualTo(near);
    }

    @Test
    void moveToward_alreadyThere_staysPut() {
        assertThat(GeoUtils.moveToward(BEIJING, BEIJING, 1.0)).isEqualTo(BEIJING);
    }
}
