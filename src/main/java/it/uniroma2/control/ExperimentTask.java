package it.uniroma2.control;

import it.uniroma2.entity.ClassifierKind;
import it.uniroma2.entity.EvaluationOutcome;
import it.uniroma2.entity.EvaluationPhase;
import it.uniroma2.entity.Recipe;
import it.uniroma2.entity.RunResult;
import it.uniroma2.entity.ValidationKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import weka.core.Instances;

/**
 * Milestone 2, Fasi 3–5: tutto il lavoro su una split training/test.
 *
 * <ol>
 *   <li>Il training viene diviso in training interno e validation: nella 10×10 con un 80/20 stratificato e casuale
 *       (la cross validation presuppone già righe indipendenti dal tempo); nell'Ordered Holdout con lo stesso 80/20 su
 *       release intere ({@link OrderedHoldoutRunner#cut}), così training interno, validation e test si susseguono nel
 *       tempo. Su SYNCOPE: release 1–17 (1.0.0–1.1.8) e 18–20 (1.2.0–1.2.2), cioè 80,2% e 19,8% del training. Simula la
 *       scelta di un team: si addestra sulle release vecchie e si controlla sulle più recenti già note.</li>
 *   <li>Per ogni classificatore e ricetta: addestramento sul training interno e misura sul validation (per scegliere);
 *       addestramento su tutto il training e misura sul test (risultato).</li>
 *   <li>Sul validation: la ricetta migliore di ogni classificatore e poi il campione tra i tre ({@link RecipeSelector}).
 *       Le scelte si riportano sul test come flag, senza mai guardare il test.</li>
 *   <li>ZeroR sul test, come baseline.</li>
 * </ol>
 */
public final class ExperimentTask implements SplitTask {

    public static final double VALIDATION_SHARE = 0.20;
    private static final int STRATIFIED_PARTS = 5;

    private final List<Integer> releaseOfRow;

    /**
     * @param releaseOfRow la release di ogni riga del dataset, nell'ordine dei dati: serve al validation dell'Ordered
     *                     Holdout, il cui training è sempre l'inizio dei dati
     */
    public ExperimentTask(List<Integer> releaseOfRow) {
        this.releaseOfRow = List.copyOf(releaseOfRow);
    }

    @Override
    public List<RunResult> evaluate(ValidationKind validation, int repetition, int fold, Instances train,
                                    Instances test) throws Exception {
        Instances[] inner = splitForValidation(validation, repetition, fold, train);
        Instances innerTrain = inner[0];
        Instances validationSet = inner[1];

        List<RunResult> validationRuns = new ArrayList<>();
        List<RunResult> testRuns = new ArrayList<>();
        for (ClassifierKind kind : ClassifierKind.competitors()) {
            for (Recipe recipe : Recipe.all()) {
                validationRuns.add(run(validation, repetition, fold, EvaluationPhase.VALIDATION, kind, recipe,
                        innerTrain, validationSet));
                testRuns.add(run(validation, repetition, fold, EvaluationPhase.TEST, kind, recipe, train, test));
            }
        }

        List<RunResult> chosenOnValidation = new ArrayList<>();
        for (ClassifierKind kind : ClassifierKind.competitors()) {
            chosenOnValidation.add(RecipeSelector.best(
                    validationRuns.stream().filter(r -> r.classifier() == kind).toList()));
        }
        RunResult champion = RecipeSelector.best(chosenOnValidation);

        List<RunResult> results = new ArrayList<>();
        for (RunResult v : validationRuns) {
            results.add(v.withChoice(chosenOnValidation.contains(v), v.equals(champion)));
        }
        for (RunResult t : testRuns) {
            boolean chosen = chosenOnValidation.stream()
                    .anyMatch(c -> c.classifier() == t.classifier() && c.recipe().equals(t.recipe()));
            boolean isChampion = champion.classifier() == t.classifier() && champion.recipe().equals(t.recipe());
            results.add(t.withChoice(chosen, isChampion));
        }
        results.add(run(validation, repetition, fold, EvaluationPhase.TEST, ClassifierKind.ZERO_R, Recipe.BASELINE,
                train, test));
        return results;
    }

    private static RunResult run(ValidationKind validation, int repetition, int fold, EvaluationPhase phase,
                                 ClassifierKind kind, Recipe recipe, Instances train, Instances test) throws Exception {
        EvaluationOutcome outcome = Evaluator.evaluate(PipelineFactory.create(kind, recipe, train), train, test);
        return new RunResult(validation, repetition, fold, phase, kind, recipe, train.numInstances(),
                test.numInstances(), outcome.features(), false, false, outcome.metrics());
    }

    /** @return {training interno, validation} */
    Instances[] splitForValidation(ValidationKind validation, int repetition, int fold, Instances train) {
        if (validation == ValidationKind.ORDERED_HOLDOUT_80_20) {
            if (train.numInstances() > releaseOfRow.size()) {
                throw new IllegalArgumentException("Training più lungo dei dati: " + train.numInstances());
            }
            int cut = OrderedHoldoutRunner.cut(releaseOfRow.subList(0, train.numInstances()), 1 - VALIDATION_SHARE);
            return new Instances[] {new Instances(train, 0, cut),
                    new Instances(train, cut, train.numInstances() - cut)};
        }
        Instances shuffled = new Instances(train);
        shuffled.randomize(new Random(1000L * repetition + fold));
        shuffled.stratify(STRATIFIED_PARTS);
        return new Instances[] {shuffled.trainCV(STRATIFIED_PARTS, 0), shuffled.testCV(STRATIFIED_PARTS, 0)};
    }
}