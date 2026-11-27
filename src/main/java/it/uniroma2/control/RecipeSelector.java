package it.uniroma2.control;

import it.uniroma2.entity.RunResult;

import java.util.Comparator;
import java.util.List;

/**
 * Sceglie sul validation (slide "Tuning" del prof: le scelte stanno nel recinto training + validation).
 *
 * <p>Criterio: la Kappa più alta. A pari Kappa (stesso valore alla terza cifra decimale) vince l'AUC più alta, poi la
 * ricetta più semplice (nessun filtro, solo feature selection, solo balancing, entrambi; undersampling prima di SMOTE),
 * infine il classificatore nell'ordine RandomForest, NaiveBayes, IBk.
 */
public final class RecipeSelector {

    private static final Comparator<RunResult> BEST_FIRST = Comparator
            .comparingLong((RunResult r) -> -Math.round(r.metrics().kappa() * 1000))
            .thenComparing((RunResult r) -> -safe(r.metrics().auc()))
            .thenComparingInt(r -> r.recipe().simplicity())
            .thenComparingInt(r -> r.classifier().ordinal());

    private RecipeSelector() {
    }

    /** Il run migliore tra i candidati, misurati sul validation. */
    public static RunResult best(List<RunResult> candidates) {
        return candidates.stream().min(BEST_FIRST)
                .orElseThrow(() -> new IllegalArgumentException("nessun candidato"));
    }

    private static double safe(double value) {
        return Double.isNaN(value) ? Double.NEGATIVE_INFINITY : value;
    }
}