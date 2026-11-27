package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.ResultsCsvWriter;
import it.uniroma2.control.ClassifierFactory;
import it.uniroma2.control.CrossValidationRunner;
import it.uniroma2.control.Evaluator;
import it.uniroma2.control.OrderedHoldoutRunner;
import it.uniroma2.control.ResultsSummarizer;
import it.uniroma2.control.SplitTask;
import it.uniroma2.control.WekaDatasetFactory;
import it.uniroma2.entity.ClassifierKind;
import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.EvaluationPhase;
import it.uniroma2.entity.Metric;
import it.uniroma2.entity.MetricSummary;
import it.uniroma2.entity.Recipe;
import it.uniroma2.entity.RunResult;
import it.uniroma2.entity.ValidationKind;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import weka.core.Instances;

/**
 * Milestone 2, Fase 2 – Baseline e prova del nove.
 *
 * <p>Prima di qualunque confronto: ZeroR (predice sempre "no") e Random Forest senza filtri, con la 10×10 cross
 * validation e con l'Ordered Holdout 80/20. Il prof: ZeroR va fatto girare all'inizio di ogni progetto, e una Kappa
 * tra 0,1 e 0,4 indica un dataset sano (sotto 0,1 c'è un errore nella pipeline).
 *
 * <p>Input: data/SYNCOPE_dataset.csv. Argomento opzionale: numero di thread (default: processori − 1).
 * Output: output/SYNCOPE_baseline_runs.csv, una riga per run.
 */
public final class RunBaseline {

    private static final Logger LOGGER = Logger.getLogger(RunBaseline.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final ClassifierKind[] CLASSIFIERS = {ClassifierKind.ZERO_R, ClassifierKind.RANDOM_FOREST};

    private RunBaseline() {
    }

    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0])
                : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        Dataset dataset = CsvReader.readDataset(Path.of("data", PROJ_NAME + "_dataset.csv"));
        Instances data = WekaDatasetFactory.create(dataset, PROJ_NAME);
        List<Integer> releaseOfRow = WekaDatasetFactory.releaseOfRows(dataset);

        SplitTask task = (validation, repetition, fold, train, test) -> {
            List<RunResult> results = new ArrayList<>();
            for (ClassifierKind kind : CLASSIFIERS) {
                results.add(new RunResult(validation, repetition, fold, EvaluationPhase.TEST, kind, Recipe.BASELINE,
                        train.numInstances(), test.numInstances(), List.of(), false, false,
                        Evaluator.evaluate(ClassifierFactory.create(kind), train, test).metrics()));
            }
            return results;
        };

        long start = System.currentTimeMillis();
        List<RunResult> runs = new ArrayList<>(new CrossValidationRunner(threads).run(data, task));
        runs.addAll(OrderedHoldoutRunner.run(data, releaseOfRow, task));
        Path output = Path.of("output", PROJ_NAME + "_baseline_runs.csv");
        ResultsCsvWriter.writeRuns(runs, output);
        long seconds = (System.currentTimeMillis() - start) / 1000;

        for (ValidationKind validation : ValidationKind.values()) {
            for (ClassifierKind kind : CLASSIFIERS) {
                List<RunResult> selected = runs.stream()
                        .filter(r -> r.validation() == validation && r.classifier() == kind).toList();
                StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-21s %-13s run %3d |",
                        validation.label(), kind.label(), selected.size()));
                for (Metric metric : Metric.values()) {
                    MetricSummary s = ResultsSummarizer.summarize(selected, metric);
                    line.append(s.runs() == 0 ? String.format(" %s n.d.", metric.label())
                            : String.format(Locale.ROOT, " %s %.3f+/-%.3f", metric.label(), s.mean(), s.std()));
                }
                LOGGER.info(line::toString);
            }
        }
        MetricSummary kappa = ResultsSummarizer.summarize(runs.stream()
                .filter(r -> r.validation() == ValidationKind.CROSS_VALIDATION_10X10
                        && r.classifier() == ClassifierKind.RANDOM_FOREST).toList(), Metric.KAPPA);
        String verdict = kappa.mean() < 0.1 ? "SOTTO 0,1: c'è un errore nella pipeline"
                : kappa.mean() <= 0.4 ? "tra 0,1 e 0,4: dataset sano" : "sopra 0,4: dataset sano, valore alto da spiegare";
        LOGGER.info(() -> String.format(Locale.ROOT, "Prova del nove: Kappa media di Random Forest (10x10) = %.3f, %s",
                kappa.mean(), verdict));
        LOGGER.info(() -> "Scritto " + output + " in " + seconds + " s con " + threads + " thread");
    }
}