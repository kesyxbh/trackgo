package com.trackgo.controller;

import com.trackgo.dto.SimulatorStatus;
import com.trackgo.simulation.SimulationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Runtime controls of the rider simulator, used by the dispatcher screen. */
@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final SimulationService simulation;

    public SimulatorController(SimulationService simulation) {
        this.simulation = simulation;
    }

    @GetMapping
    public SimulatorStatus status() {
        return simulation.status();
    }

    /** Freezes every rider in place. Handy for demonstrating the stalled-rider alert. */
    @PostMapping("/pause")
    public SimulatorStatus pause() {
        simulation.pause();
        return simulation.status();
    }

    @PostMapping("/resume")
    public SimulatorStatus resume() {
        simulation.resume();
        return simulation.status();
    }
}
