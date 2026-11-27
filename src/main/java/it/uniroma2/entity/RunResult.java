package it.uniroma2.entity;

import java.util.List;

/**
 * Il risultato di un run: un classificatore con una ricetta, addestrato e misurato su una split.
 *
 * @param repetition ripetizione della cross validation (1–10); 1 per l'Ordered Holdout
 * @param fold       fold della ripetizione (1–10); 1 per l'Ordered Holdout
 * @param phase      validation (solo per scegliere) o test (risultato)
 * @param trainSize  righe del training (prima del balancing)
 * @param testSize   righe su cui si misura (validation o test)
 * @param features   colonne scelte dalla feature selection; vuota se non c'è feature selection
 * @param chosen     sul test: la ricetta scelta sul validation per questo classificatore in questa split
 * @param champion   sul test: il classificatore (con la sua ricetta scelta) scelto come migliore sul validation
 */
public record RunResult(ValidationKind validation, int repetition, int fold, EvaluationPhase phase,
                        ClassifierKind classifier, Recipe recipe, int trainSize, int testSize, List<String> features,
                        boolean chosen, boolean champion, ModelMetrics metrics) {

    /** Lo stesso run con i flag della scelta sul validation. */
    public RunResult withChoice(boolean isChosen, boolean isChampion) {
        return new RunResult(validation, repetition, fold, phase, classifier, recipe, trainSize, testSize, features,
                isChosen, isChampion, metrics);
    }
}