package com.trackgo.service;

/**
 * @param remainingKm distance the rider still has to cover, or null when it cannot be known yet
 * @param etaMinutes  remainingKm divided by the speed, or null when remainingKm is null
 * @param speedKmh    the speed the estimate assumes
 */
public record EtaEstimate(Double remainingKm, Double etaMinutes, double speedKmh) {
}
