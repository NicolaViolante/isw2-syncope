package it.uniroma2.boundary;

import it.uniroma2.entity.ConfigurationSummary;
import it.uniroma2.entity.FeatureSelectionReport;
import it.uniroma2.entity.Metric;
import it.uniroma2.entity.MetricSummary;
import it.uniroma2.entity.ModelMetrics;
import it.uniroma2.entity.RunResult;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Scrive i risultati della Milestone 2: le run, il riassunto per configurazione e le strade di feature selection. */
public final class ResultsCsvWriter {

    private static final String HEADER = "Validation,Repetition,Fold,Phase,Classifier,FeatureSelection,Balancing,"
            + "TrainSize,TestSize,Chosen,Champion,Accuracy,Precision,Recall,F1,AUC,Kappa,NPofB20,TP,FP,TN,FN,"
            + "NumFeatures,Features";

    private ResultsCsvWriter() {
    }

    public static void writeRuns(List<RunResult> runs, Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println(HEADER);
            for (RunResult r : runs) {
                ModelMetrics m = r.metrics();
                out.println(String.format(Locale.ROOT,
                        "%s,%d,%d,%s,%s,%s,%s,%d,%d,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f,%d,%d,%d,%d,%s,%s",
                        r.validation().label(), r.repetition(), r.fold(), r.phase().label(), r.classifier().label(),
                        r.recipe().featureSelection().label(), r.recipe().balancing().label(), r.trainSize(),
                        r.testSize(), r.chosen() ? "yes" : "no", r.champion() ? "yes" : "no", m.accuracy(),
                        m.precision(), m.recall(), m.f1(), m.auc(), m.kappa(), m.npofb20(), m.tp(), m.fp(), m.tn(),
                        m.fn(), r.features().isEmpty() ? "all" : String.valueOf(r.features().size()),
                        String.join(";", r.features())));
            }
        }
    }

    /** Una riga per configurazione: media, mediana e deviazione standard di ogni metrica sul test. */
    public static void writeSummary(List<ConfigurationSummary> summaries, Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        StringBuilder header = new StringBuilder("Validation,Classifier,FeatureSelection,Balancing,Runs,TimesChosen,"
                + "TimesChampion");
        for (Metric metric : Metric.values()) {
            header.append(',').append(metric.label()).append("_mean,").append(metric.label()).append("_median,")
                    .append(metric.label()).append("_std");
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println(header);
            for (ConfigurationSummary s : summaries) {
                StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%s,%s,%s,%s,%d,%d,%d",
                        s.validation().label(), s.classifier().label(), s.recipe().featureSelection().label(),
                        s.recipe().balancing().label(), s.metrics().get(Metric.KAPPA).runs(), s.timesChosen(),
                        s.timesChampion()));
                for (Metric metric : Metric.values()) {
                    MetricSummary m = s.metrics().get(metric);
                    line.append(String.format(Locale.ROOT, ",%.4f,%.4f,%.4f", m.mean(), m.median(), m.std()));
                }
                out.println(line);
            }
        }
    }

    /** Una riga per strada di feature selection: colonne tenute e scartate, e i punteggi se è un ranking. */
    public static void writeFeatureSelection(List<FeatureSelectionReport> reports, Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println("Method,NumKept,Kept,Dropped,Scores,Seconds");
            for (FeatureSelectionReport r : reports) {
                String scores = r.scores().entrySet().stream()
                        .map(e -> String.format(Locale.ROOT, "%s=%.4f", e.getKey(), e.getValue()))
                        .collect(Collectors.joining(";"));
                out.println(String.format(Locale.ROOT, "\"%s\",%d,%s,%s,%s,%.1f", r.method(), r.kept().size(),
                        String.join(";", r.kept()), String.join(";", r.dropped()), scores, r.seconds()));
            }
        }
    }
}