package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.ResultsCsvWriter;
import it.uniroma2.control.CrossValidationRunner;
import it.uniroma2.control.ExperimentTask;
import it.uniroma2.control.OrderedHoldoutRunner;
import it.uniroma2.control.ResultsSummarizer;
import it.uniroma2.control.SplitTask;
import it.uniroma2.control.WekaDatasetFactory;
import it.uniroma2.entity.ClassifierKind;
import it.uniroma2.entity.ConfigurationSummary;
import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.EvaluationPhase;
import it.uniroma2.entity.Metric;
import it.uniroma2.entity.MetricSummary;
import it.uniroma2.entity.Recipe;
import it.uniroma2.entity.RunResult;
import it.uniroma2.entity.ValidationKind;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Logger;

import weka.core.Instances;

/**
 * Milestone 2, Fasi 3–5 – L'esperimento: 3 classificatori × 6 ricette, scelte sul validation, risultati sul test.
 *
 * <p>Per ogni split di training e test (100 della 10×10 cross validation e 1 dell'Ordered Holdout 80/20 su release
 * intere) il lavoro è quello di {@link ExperimentTask}: il validation sceglie, il test misura. Poi, sul test:
 * <ol>
 *   <li>il migliore: per ogni classificatore la ricetta scelta più spesso dal validation, e il campione scelto più
 *       spesso, con i loro numeri sul test (risultato principale; nell'80/20 c'è una sola scelta);</li>
 *   <li>controllo: la media delle versioni scelte split per split, cioè quanto rende fidarsi ogni volta del
 *       validation;</li>
 *   <li>confronti 2 e 3: tutte le configurazioni, per vedere l'effetto di feature selection e balancing su ogni
 *       classificatore e i classificatori a parità di ricetta. Non servono a scegliere.</li>
 * </ol>
 * Niente test statistici (come Gianpaolo e Valentina): media, mediana e deviazione standard su tutte le run.
 *
 * <p>Input: data/SYNCOPE_dataset.csv. Argomenti opzionali: numero di thread (default: processori − 1) e ripetizioni
 * della 10×10 (default 10; meno solo per stimare i tempi). Output: output/SYNCOPE_m2_runs.csv (una riga per run, anche
 * quelle sul validation) e output/SYNCOPE_m2_summary.csv (una riga per configurazione, sul test).
 */
public final class RunExperiment {

    private static final Logger LOGGER = Logger.getLogger(RunExperiment.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Metric[] SHOWN = {Metric.KAPPA, Metric.AUC, Metric.PRECISION, Metric.RECALL, Metric.F1,
            Metric.NPOFB20};

    private RunExperiment() {
    }

    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0])
                : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        int repetitions = args.length > 1 ? Integer.parseInt(args[1]) : CrossValidationRunner.REPETITIONS;
        Dataset dataset = CsvReader.readDataset(Path.of("data", PROJ_NAME + "_dataset.csv"));
        Instances data = WekaDatasetFactory.create(dataset, PROJ_NAME);
        List<Integer> releaseOfRow = WekaDatasetFactory.releaseOfRows(dataset);

        int splits = repetitions * CrossValidationRunner.FOLDS + 1;
        long start = System.currentTimeMillis();
        AtomicInteger done = new AtomicInteger();
        ExperimentTask experiment = new ExperimentTask(releaseOfRow);
        SplitTask task = (validation, repetition, fold, train, test) -> {
            List<RunResult> results = experiment.evaluate(validation, repetition, fold, train, test);
            int finished = done.incrementAndGet();
            long elapsed = (System.currentTimeMillis() - start) / 1000;
            LOGGER.info(() -> String.format(Locale.ROOT, "Split %d/%d finita (%s, ripetizione %d, fold %d): %d s",
                    finished, splits, validation.label(), repetition, fold, elapsed));
            return results;
        };

        LOGGER.info(() -> String.format("Avvio: %d split, %d thread, %d addestramenti per split", splits, threads,
                2 * ClassifierKind.competitors().length * Recipe.all().size() + 1));
        List<RunResult> runs = new ArrayList<>(new CrossValidationRunner(threads, repetitions).run(data, task));
        runs.addAll(OrderedHoldoutRunner.run(data, releaseOfRow, task));
        long seconds = (System.currentTimeMillis() - start) / 1000;

        List<ConfigurationSummary> summaries = ResultsSummarizer.byConfiguration(runs);
        Path runsFile = Path.of("output", PROJ_NAME + "_m2_runs.csv");
        Path summaryFile = Path.of("output", PROJ_NAME + "_m2_summary.csv");
        ResultsCsvWriter.writeRuns(runs, runsFile);
        ResultsCsvWriter.writeSummary(summaries, summaryFile);

        for (ValidationKind validation : ValidationKind.values()) {
            logValidation(runs, summaries, validation);
        }
        logWinners(summaries);
        LOGGER.info(() -> "Scritti " + runsFile + " (" + runs.size() + " run) e " + summaryFile + " in " + seconds
                + " s con " + threads + " thread");
    }

    private static void logValidation(List<RunResult> runs, List<ConfigurationSummary> summaries,
                                      ValidationKind validation) {
        String v = validation.label();
        List<RunResult> test = runs.stream()
                .filter(r -> r.validation() == validation && r.phase() == EvaluationPhase.TEST).toList();
        if (test.isEmpty()) {
            return;
        }

        LOGGER.info(() -> "== " + v + " - scelte sul validation (quante split hanno scelto ogni ricetta)");
        for (ClassifierKind kind : ClassifierKind.competitors()) {
            StringBuilder line = new StringBuilder(String.format("%-13s", kind.label()));
            for (ConfigurationSummary s : summaries) {
                if (s.validation() == validation && s.classifier() == kind) {
                    line.append(String.format(Locale.ROOT, " | %s %d (campione %d)", s.recipe().label(),
                            s.timesChosen(), s.timesChampion()));
                }
            }
            LOGGER.info(line::toString);
        }

        LOGGER.info(() -> "== " + v + " - il migliore: la ricetta scelta piu' spesso dal validation, sul test");
        for (ClassifierKind kind : ClassifierKind.competitors()) {
            ConfigurationSummary best = ResultsSummarizer.mostChosen(summaries, validation, kind);
            logLine(String.format(Locale.ROOT, "%s %s (scelta %d/%d)", kind.label(), best.recipe().label(),
                            best.timesChosen(), best.metrics().get(Metric.KAPPA).runs()),
                    select(test, r -> r.classifier() == kind && r.recipe().equals(best.recipe())));
        }
        ConfigurationSummary champion = ResultsSummarizer.mostChampion(summaries, validation);
        logLine(String.format(Locale.ROOT, "Campione: %s %s (%d/%d)", champion.classifier().label(),
                        champion.recipe().label(), champion.timesChampion(), champion.metrics().get(Metric.KAPPA).runs()),
                select(test, r -> r.classifier() == champion.classifier() && r.recipe().equals(champion.recipe())));
        logLine(ClassifierKind.ZERO_R.label(), select(test, r -> r.classifier() == ClassifierKind.ZERO_R));

        LOGGER.info(() -> "== " + v + " - controllo: media delle versioni scelte split per split, misurate sul test");
        for (ClassifierKind kind : ClassifierKind.competitors()) {
            logLine(kind.label() + " (scelta della split)", select(test, r -> r.classifier() == kind && r.chosen()));
        }
        logLine("Campione della split", select(test, RunResult::champion));

        LOGGER.info(() -> "== " + v + " - confronti 2 e 3: tutte le configurazioni sul test (ZeroR: Kappa 0, AUC 0,5)");
        for (Recipe recipe : Recipe.all()) {
            for (ClassifierKind kind : ClassifierKind.competitors()) {
                logLine(kind.label() + " " + recipe.label(),
                        select(test, r -> r.classifier() == kind && r.recipe().equals(recipe)));
            }
        }

        Map<String, List<String>> features = ResultsSummarizer.selectedFeaturesBySplit(runs, validation);
        Map<String, Integer> frequency = ResultsSummarizer.featureFrequency(runs, validation);
        LOGGER.info(() -> String.format(Locale.ROOT,
                "== %s - feature tenute da CFS-backward (uguali per tutti i classificatori e balancing): media %.1f "
                        + "su %d split; %s", v,
                features.values().stream().mapToInt(List::size).average().orElse(Double.NaN), features.size(),
                frequency));
    }

    /**
     * A posteriori, solo per confronto: la configurazione con la Kappa media più alta sul test, e quante volte il
     * validation l'aveva scelta come campione. Serve a vedere se 10×10 e Ordered Holdout premiano la stessa.
     */
    private static void logWinners(List<ConfigurationSummary> summaries) {
        for (ValidationKind validation : ValidationKind.values()) {
            summaries.stream()
                    .filter(s -> s.validation() == validation && s.classifier() != ClassifierKind.ZERO_R)
                    .max(Comparator.comparingDouble(s -> s.metrics().get(Metric.KAPPA).mean()))
                    .ifPresent(best -> LOGGER.info(() -> String.format(Locale.ROOT,
                            "A posteriori, migliore sul test con %s: %s %s (Kappa media %.3f); campione %d volte su %d",
                            validation.label(), best.classifier().label(), best.recipe().label(),
                            best.metrics().get(Metric.KAPPA).mean(), best.timesChampion(),
                            best.metrics().get(Metric.KAPPA).runs())));
        }
    }

    private static List<RunResult> select(List<RunResult> runs, Predicate<RunResult> filter) {
        return runs.stream().filter(filter).toList();
    }

    private static void logLine(String name, List<RunResult> selected) {
        Map<Metric, MetricSummary> metrics = ResultsSummarizer.summarizeAll(selected);
        StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-44s run %3d |", name, selected.size()));
        for (Metric metric : SHOWN) {
            MetricSummary s = metrics.get(metric);
            line.append(s.runs() == 0 ? String.format(" %s n.d.", metric.label())
                    : String.format(Locale.ROOT, " %s %.3f+/-%.3f", metric.label(), s.mean(), s.std()));
        }
        LOGGER.info(line::toString);
    }
}