package com.trackgo.geo;

/**
 * Small, dependency-free geographic helpers. Everything here is a pure function, so it is trivial
 * to unit test and safe to call from any thread.
 */
public final class GeoUtils {

    /** Mean Earth radius in kilometres (IUGG). */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtils() {
    }

    /**
     * Great-circle distance between two points using the haversine formula.
     * Accurate to well under 1% for city-scale distances, which is plenty for dispatching.
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double sinHalfLat = Math.sin(dLat / 2);
        double sinHalfLng = Math.sin(dLng / 2);
        double a = sinHalfLat * sinHalfLat
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinHalfLng * sinHalfLng;
        // min(1, ...) guards against floating point drift pushing the square root above 1.
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static double haversineKm(GeoPoint a, GeoPoint b) {
        return haversineKm(a.lat(), a.lng(), b.lat(), b.lng());
    }

    /**
     * Moves from {@code from} towards {@code to} by at most {@code stepKm} along a straight line
     * (linear interpolation of latitude and longitude). If the target is within one step the target
     * itself is returned, so a moving rider lands exactly on the destination instead of oscillating
     * around it.
     *
     * <p>Linear interpolation of latitude/longitude is not a great circle: it is accurate for the
     * city-scale trips of this application, but over ~1,000 km a step comes out about 1% short.
     */
    public static GeoPoint moveToward(GeoPoint from, GeoPoint to, double stepKm) {
        double distance = haversineKm(from, to);
        if (distance <= stepKm || distance == 0) {
            return to;
        }
        double fraction = stepKm / distance;
        return new GeoPoint(
                from.lat() + (to.lat() - from.lat()) * fraction,
                from.lng() + (to.lng() - from.lng()) * fraction);
    }
}
