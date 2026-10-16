package com.trackgo.simulation;

/** Counts of what happened during one simulation tick. */
public record TickSummary(int moved, int pickedUp, int delivered) {

    public boolean isEmpty() {
        return moved == 0 && pickedUp == 0 && delivered == 0;
    }
}
