package it.uniroma2.control;

import it.uniroma2.entity.ClassifierKind;
import it.uniroma2.entity.ConfigurationSummary;
import it.uniroma2.entity.EvaluationPhase;
import it.uniroma2.entity.Metric;
import it.uniroma2.entity.MetricSummary;
import it.uniroma2.entity.Recipe;
import it.uniroma2.entity.RunResult;
import it.uniroma2.entity.ValidationKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Riassume una metrica su più run con media, mediana e deviazione standard: al posto dei test statistici (non usati,
 * come Gianpaolo e Valentina) mostrano quanto le run variano e quando due configurazioni si sovrappongono.
 *
 * <p>Precision e F1 non sono definite quando il modello non predice mai "yes" (Weka restituisce NaN, per esempio
 * sempre con ZeroR): quei run non entrano nel riassunto, e {@link MetricSummary#runs()} dice su quanti run è calcolato.
 */
public final class ResultsSummarizer {

    private ResultsSummarizer() {
    }

    public static MetricSummary summarize(List<RunResult> runs, Metric metric) {
        double[] values = runs.stream().mapToDouble(r -> r.metrics().get(metric)).filter(v -> !Double.isNaN(v))
                .sorted().toArray();
        int n = values.length;
        if (n == 0) {
            return new MetricSummary(0, Double.NaN, Double.NaN, Double.NaN);
        }
        double mean = Arrays.stream(values).average().orElse(Double.NaN);
        double median = n % 2 == 1 ? values[n / 2] : (values[n / 2 - 1] + values[n / 2]) / 2;
        double squares = Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum();
        double std = n > 1 ? Math.sqrt(squares / (n - 1)) : 0.0;
        return new MetricSummary(n, mean, median, std);
    }

    /**
     * Il migliore di un classificatore per maggioranza: la ricetta che il validation ha scelto in più split. A pari
     * conteggio vale la ricetta più semplice. Il test non entra nella scelta.
     */
    public static ConfigurationSummary mostChosen(List<ConfigurationSummary> summaries, ValidationKind validation,
                                                  ClassifierKind kind) {
        return summaries.stream()
                .filter(s -> s.validation() == validation && s.classifier() == kind)
                .min(Comparator.comparingInt(ConfigurationSummary::timesChosen).reversed()
                        .thenComparingInt(s -> s.recipe().simplicity()))
                .orElseThrow();
    }

    /**
     * Il campione per maggioranza: la configurazione che il validation ha indicato come campione in più split. A pari
     * conteggio valgono l'ordine dei classificatori e poi la ricetta più semplice.
     */
    public static ConfigurationSummary mostChampion(List<ConfigurationSummary> summaries, ValidationKind validation) {
        return summaries.stream()
                .filter(s -> s.validation() == validation && s.classifier() != ClassifierKind.ZERO_R)
                .min(Comparator.comparingInt(ConfigurationSummary::timesChampion).reversed()
                        .thenComparingInt(s -> s.classifier().ordinal())
                        .thenComparingInt(s -> s.recipe().simplicity()))
                .orElseThrow();
    }

    /** Tutte le metriche riassunte sugli stessi run. */
    public static Map<Metric, MetricSummary> summarizeAll(List<RunResult> runs) {
        Map<Metric, MetricSummary> metrics = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            metrics.put(metric, summarize(runs, metric));
        }
        return metrics;
    }

    /**
     * Quante split hanno tenuto ogni feature, con la feature selection, in una validazione: dai run sul test (il
     * filtro è calcolato su tutto il training della split). Ordinate dalla più tenuta; a pari conteggio, per nome.
     */
    public static Map<String, Integer> featureFrequency(List<RunResult> runs, ValidationKind validation) {
        Map<String, Integer> counts = new TreeMap<>();
        for (List<String> features : selectedFeaturesBySplit(runs, validation).values()) {
            for (String feature : features) {
                counts.merge(feature, 1, Integer::sum);
            }
        }
        Map<String, Integer> ordered = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> ordered.put(e.getKey(), e.getValue()));
        return ordered;
    }

    /**
     * Le feature tenute in ogni split. La feature selection guarda solo il training, prima del balancing e senza il
     * classificatore: in una split deve dare le stesse colonne a tutte le ricette con CFS e a tutti i classificatori.
     *
     * @throws IllegalStateException se in una split due run con CFS hanno colonne diverse
     */
    public static Map<String, List<String>> selectedFeaturesBySplit(List<RunResult> runs, ValidationKind validation) {
        Map<String, List<String>> bySplit = new TreeMap<>();
        for (RunResult r : runs) {
            if (r.phase() != EvaluationPhase.TEST || r.validation() != validation || r.features().isEmpty()) {
                continue;
            }
            String split = String.format("%02d-%02d", r.repetition(), r.fold());
            List<String> previous = bySplit.putIfAbsent(split, r.features());
            if (previous != null && !previous.equals(r.features())) {
                throw new IllegalStateException("Feature diverse nella split " + split + ": " + previous + " e "
                        + r.features());
            }
        }
        return bySplit;
    }

    /**
     * Una riga per validazione, classificatore e ricetta, con le metriche del test riassunte su tutte le run e quante
     * volte la configurazione è stata scelta sul validation. ZeroR compare con la sola ricetta senza filtri.
     */
    public static List<ConfigurationSummary> byConfiguration(List<RunResult> runs) {
        List<ConfigurationSummary> summaries = new ArrayList<>();
        for (ValidationKind validation : ValidationKind.values()) {
            for (ClassifierKind kind : ClassifierKind.values()) {
                for (Recipe recipe : Recipe.all()) {
                    List<RunResult> selected = runs.stream()
                            .filter(r -> r.phase() == EvaluationPhase.TEST && r.validation() == validation
                                    && r.classifier() == kind && r.recipe().equals(recipe))
                            .toList();
                    if (selected.isEmpty()) {
                        continue;
                    }
                    Map<Metric, MetricSummary> metrics = summarizeAll(selected);
                    int chosen = (int) selected.stream().filter(RunResult::chosen).count();
                    int champion = (int) selected.stream().filter(RunResult::champion).count();
                    summaries.add(new ConfigurationSummary(validation, kind, recipe, metrics, chosen, champion));
                }
            }
        }
        return summaries;
    }

    /**
     * Per i box plot dei confronti 2 e 3: classificatore → ricetta → valori della metrica sul test, run per run (senza
     * i NaN). La baseline del confronto 2 è la ricetta senza filtri dello stesso classificatore; ZeroR è nel box plot
     * delle versioni scelte.
     */
    public static Map<String, Map<String, List<Double>>> valuesByRecipe(List<RunResult> runs,
                                                                        ValidationKind validation, Metric metric) {
        Map<String, Map<String, List<Double>>> values = new LinkedHashMap<>();
        for (ClassifierKind kind : ClassifierKind.competitors()) {
            Map<String, List<Double>> byRecipe = new LinkedHashMap<>();
            for (Recipe recipe : Recipe.all()) {
                byRecipe.put(recipe.label(), values(runs, validation, metric,
                        r -> r.classifier() == kind && r.recipe().equals(recipe)));
            }
            values.put(kind.label(), byRecipe);
        }
        return values;
    }

    /**
     * Per il box plot del confronto 1: una sola serie, con la versione scelta sul validation di ogni classificatore,
     * il campione e ZeroR.
     */
    public static Map<String, Map<String, List<Double>>> valuesOfChosen(List<RunResult> runs,
                                                                        ValidationKind validation, Metric metric) {
        Map<String, List<Double>> byVersion = new LinkedHashMap<>();
        for (ClassifierKind kind : ClassifierKind.competitors()) {
            byVersion.put(kind.label() + " (scelta)",
                    values(runs, validation, metric, r -> r.classifier() == kind && r.chosen()));
        }
        byVersion.put("Campione", values(runs, validation, metric, RunResult::champion));
        byVersion.put(ClassifierKind.ZERO_R.label(),
                values(runs, validation, metric, r -> r.classifier() == ClassifierKind.ZERO_R));
        Map<String, Map<String, List<Double>>> values = new LinkedHashMap<>();
        values.put("Versioni scelte sul validation", byVersion);
        return values;
    }

    private static List<Double> values(List<RunResult> runs, ValidationKind validation, Metric metric,
                                       Predicate<RunResult> filter) {
        return runs.stream()
                .filter(r -> r.phase() == EvaluationPhase.TEST && r.validation() == validation && filter.test(r))
                .map(r -> r.metrics().get(metric))
                .filter(v -> !Double.isNaN(v))
                .toList();
    }
}