package com.trackgo.anomaly;

import java.util.Optional;

/**
 * Turns the facts of a detected anomaly into a note for the customer. The detection itself is
 * plain rules; this seam exists so a language model can phrase the note without the rest of the
 * application knowing the model exists. An empty result means "I could not, use the fallback text".
 */
public interface AnomalyExplainer {

    Optional<String> explain(AnomalyFacts facts);
}
