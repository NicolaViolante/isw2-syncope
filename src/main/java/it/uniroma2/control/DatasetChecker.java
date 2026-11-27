package it.uniroma2.control;

import it.uniroma2.entity.CheckResult;
import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.DatasetRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Fase 10: controlla il dataset finale prima di consegnarlo.
 *
 * <p>Controlli bloccanti (una sola violazione è un errore):
 * <ul>
 *   <li>struttura: progetto, release del dataset, nessuna riga duplicata, una riga per ogni classe
 *       dell'inventario (Fase 3) e nessuna in più;</li>
 *   <li>valori: nessuna cella vuota o non numerica, nessun valore negativo (tranne Churn e MaxChurn);</li>
 *   <li>coerenza tra colonne: per esempio NFix ≤ NR, MaxLOCAdded ≤ LOCAdded, valore della release ≤ valore
 *       dall'inizio, WeightedAge &lt; Age + 1 (Age è in settimane intere, WeightedAge con i decimali);</li>
 *   <li>coerenza tra fasi: feature e NSmells uguali al dataset della Fase 6, Bugginess uguale a quella che si
 *       ottiene rieseguendo il labeling della Fase 9.</li>
 * </ul>
 *
 * <p>Controlli informativi (dati da dichiarare nel report, non errori): classi senza commit nella release,
 * classi nate in un merge (NR_Hist = 0), e i casi in cui NR_Hist o Age di una classe scendono da una release
 * alla successiva. Questi ultimi capitano quando il tag di una release non contiene quello della precedente
 * (release di manutenzione su un altro ramo) o quando la storia Git della classe riparte (file cancellato e
 * ripristinato, modulo copiato).
 */
public final class DatasetChecker {

    private static final String WEIGHTED_AGE = "WeightedAge";
    private static final String NR = "NR";
    private static final String NR_HIST = "NR_Hist";
    private static final String AGE = "Age";
    private static final Set<String> MAY_BE_NEGATIVE = Set.of("Churn", "MaxChurn");
    private static final List<String> WITH_HISTORY = List.of(NR, "NFix", "NAuth", "LOCAdded", "ChangeSetSize");
    private static final List<String> RELEASE_PROCESS = List.of("NFix", "NAuth", "LOCAdded", "MaxLOCAdded",
            "Churn", "MaxChurn", "ChangeSetSize", "MaxChangeSet");

    /** Una regola sulla singola riga: deve valere per tutte le righe. */
    private record Rule(String description, Predicate<Values> holds) {
    }

    /** I valori numerici di una riga, letti per nome di feature. */
    private static final class Values {
        private final Map<String, Integer> index;
        private final double[] values;

        Values(Map<String, Integer> index, double[] values) {
            this.index = index;
            this.values = values;
        }

        double get(String name) {
            return values[index.get(name)];
        }
    }

    /** Conta le violazioni di un controllo e ricorda la prima. */
    private static final class Tally {
        private final String description;
        private final boolean blocking;
        private long count;
        private String example = "";

        Tally(String description, boolean blocking) {
            this.description = description;
            this.blocking = blocking;
        }

        void add(String where) {
            if (count == 0) {
                example = where;
            }
            count++;
        }

        CheckResult result() {
            return new CheckResult(description, count, example, blocking);
        }
    }

    private final List<Rule> rules = new ArrayList<>();

    public DatasetChecker() {
        rules.add(new Rule("LOC > 0", v -> v.get("LOC") > 0));
        rules.add(new Rule("CyclomaticComplexity >= NOM", v -> v.get("CyclomaticComplexity") >= v.get("NOM")));
        rules.add(new Rule("NFix <= NR e NAuth <= NR", v -> v.get("NFix") <= v.get(NR) && v.get("NAuth") <= v.get(NR)));
        rules.add(new Rule("NR = 0 => tutte le metriche della release a 0",
                v -> v.get(NR) > 0 || RELEASE_PROCESS.stream().allMatch(f -> v.get(f) == 0)));
        rules.add(new Rule("NR >= 1 => NAuth >= 1, MaxChangeSet >= 1 e ChangeSetSize >= NR",
                v -> v.get(NR) == 0 || (v.get("NAuth") >= 1 && v.get("MaxChangeSet") >= 1
                        && v.get("ChangeSetSize") >= v.get(NR))));
        rules.add(new Rule("MaxLOCAdded <= LOCAdded e MaxChangeSet <= ChangeSetSize",
                v -> v.get("MaxLOCAdded") <= v.get("LOCAdded") && v.get("MaxChangeSet") <= v.get("ChangeSetSize")));
        rules.add(new Rule("Churn <= LOCAdded e MaxChurn <= MaxLOCAdded",
                v -> v.get("Churn") <= v.get("LOCAdded") && v.get("MaxChurn") <= v.get("MaxLOCAdded")));
        rules.add(new Rule("valore della release <= valore dall'inizio (NR, NFix, NAuth, LOCAdded, ChangeSetSize)",
                v -> WITH_HISTORY.stream().allMatch(f -> v.get(f) <= v.get(f + "_Hist"))));
        rules.add(new Rule("NFix_Hist <= NR_Hist e NAuth_Hist <= NR_Hist",
                v -> v.get("NFix_Hist") <= v.get(NR_HIST) && v.get("NAuth_Hist") <= v.get(NR_HIST)));
        rules.add(new Rule("WeightedAge < Age + 1", v -> v.get(WEIGHTED_AGE) < v.get(AGE) + 1));
        rules.add(new Rule("NR_Hist = 0 => WeightedAge = Age (classi nate in un merge)",
                v -> v.get(NR_HIST) > 0 || v.get(WEIGHTED_AGE) == v.get(AGE)));
    }

    /**
     * @param dataset   il dataset finale (Fase 9)
     * @param unlabeled il dataset senza etichetta (Fase 6)
     * @param relabeled il dataset ottenuto rieseguendo il labeling sui file congelati
     * @param classes   l'inventario della Fase 3: per Release ID, percorso → Class ID
     * @param project   il nome del progetto atteso nella colonna Project
     */
    public List<CheckResult> check(Dataset dataset, Dataset unlabeled, Dataset relabeled,
                                   Map<Integer, Map<String, String>> classes, String project) {
        List<CheckResult> results = new ArrayList<>();
        results.addAll(checkStructure(dataset, classes, project));
        results.addAll(checkValues(dataset));
        results.addAll(checkPhases(dataset, unlabeled, relabeled));
        results.addAll(informative(dataset, classes));
        return results;
    }

    private List<CheckResult> checkStructure(Dataset dataset, Map<Integer, Map<String, String>> classes,
                                             String project) {
        Tally header = new Tally("intestazione: 20 feature, poi NSmells e Bugginess", true);
        if (dataset.featureNames().size() != 20) {
            header.add(dataset.featureNames().size() + " feature");
        }
        Tally projectAndRelease = new Tally("Project = " + project + " e ReleaseID tra le release del dataset", true);
        Tally duplicates = new Tally("nessuna riga duplicata (ReleaseID, Class)", true);
        Tally extra = new Tally("nessuna riga fuori dall'inventario della Fase 3", true);
        Set<String> seen = new HashSet<>();
        for (DatasetRow r : dataset.rows()) {
            String where = where(r);
            if (!project.equals(r.project()) || !classes.containsKey(r.releaseId())) {
                projectAndRelease.add(where);
            }
            if (!seen.add(r.releaseId() + "|" + r.path())) {
                duplicates.add(where);
            }
            Map<String, String> atRelease = classes.get(r.releaseId());
            if (atRelease != null && !atRelease.containsKey(r.path())) {
                extra.add(where);
            }
        }
        Tally missing = new Tally("ogni classe dell'inventario ha la sua riga", true);
        classes.forEach((release, paths) -> paths.keySet().forEach(path -> {
            if (!seen.contains(release + "|" + path)) {
                missing.add("release " + release + ", " + path);
            }
        }));
        return List.of(header.result(), projectAndRelease.result(), duplicates.result(), extra.result(),
                missing.result());
    }

    private List<CheckResult> checkValues(Dataset dataset) {
        List<String> names = dataset.featureNames();
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            index.put(names.get(i), i);
        }
        Tally parse = new Tally("nessun valore vuoto o non numerico (interi, WeightedAge decimale)", true);
        Tally negative = new Tally("nessun valore negativo (tranne Churn e MaxChurn) e NSmells >= 0", true);
        List<Tally> ruleTallies = rules.stream().map(rule -> new Tally(rule.description(), true)).toList();
        for (DatasetRow r : dataset.rows()) {
            double[] values = parse(names, r.features());
            if (values == null) {
                parse.add(where(r));
                continue;
            }
            boolean nonNegative = r.nSmells() >= 0;
            for (int i = 0; i < names.size(); i++) {
                if (values[i] < 0 && !MAY_BE_NEGATIVE.contains(names.get(i))) {
                    nonNegative = false;
                }
            }
            if (!nonNegative) {
                negative.add(where(r));
            }
            Values v = new Values(index, values);
            for (int k = 0; k < rules.size(); k++) {
                if (!rules.get(k).holds().test(v)) {
                    ruleTallies.get(k).add(where(r));
                }
            }
        }
        List<CheckResult> results = new ArrayList<>(List.of(parse.result(), negative.result()));
        ruleTallies.forEach(t -> results.add(t.result()));
        return results;
    }

    /** I valori della riga come numeri; null se una cella è vuota o non è un numero del tipo atteso. */
    private static double[] parse(List<String> names, List<String> features) {
        if (features.size() != names.size()) {
            return null;
        }
        double[] values = new double[features.size()];
        try {
            for (int i = 0; i < features.size(); i++) {
                String cell = features.get(i);
                values[i] = WEIGHTED_AGE.equals(names.get(i)) ? Double.parseDouble(cell) : Long.parseLong(cell);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return values;
    }

    private List<CheckResult> checkPhases(Dataset dataset, Dataset unlabeled, Dataset relabeled) {
        Tally features = new Tally("feature e NSmells uguali al dataset della Fase 6", true);
        Tally labels = new Tally("Bugginess uguale a quella del labeling rieseguito (Fase 9)", true);
        Map<String, DatasetRow> before = byKey(unlabeled);
        Map<String, DatasetRow> again = byKey(relabeled);
        if (before.size() != dataset.rows().size()) {
            features.add("righe: " + before.size() + " nella Fase 6, " + dataset.rows().size() + " nel finale");
        }
        for (DatasetRow r : dataset.rows()) {
            String key = r.releaseId() + "|" + r.path();
            DatasetRow b = before.get(key);
            if (b == null || !b.features().equals(r.features()) || b.nSmells() != r.nSmells()) {
                features.add(where(r));
            }
            DatasetRow a = again.get(key);
            if (a == null || a.buggy() != r.buggy()) {
                labels.add(where(r));
            }
        }
        return List.of(features.result(), labels.result());
    }

    private List<CheckResult> informative(Dataset dataset, Map<Integer, Map<String, String>> classes) {
        List<String> names = dataset.featureNames();
        int nr = names.indexOf(NR);
        int nrHist = names.indexOf(NR_HIST);
        int age = names.indexOf(AGE);
        Tally untouched = new Tally("righe senza commit nella release (NR = 0)", false);
        Tally mergeBorn = new Tally("righe di classi nate in un merge (NR_Hist = 0)", false);
        Map<String, TreeMap<Integer, DatasetRow>> byClassId = new HashMap<>();
        for (DatasetRow r : dataset.rows()) {
            if ("0".equals(r.features().get(nr))) {
                untouched.add(where(r));
            }
            if ("0".equals(r.features().get(nrHist))) {
                mergeBorn.add(where(r));
            }
            String classId = classes.getOrDefault(r.releaseId(), Map.of()).get(r.path());
            if (classId != null) {
                byClassId.computeIfAbsent(classId, k -> new TreeMap<>()).put(r.releaseId(), r);
            }
        }
        Tally nrHistDown = new Tally("NR_Hist che scende tra due release consecutive della stessa classe", false);
        Tally ageDown = new Tally("Age che scende tra due release consecutive della stessa classe", false);
        for (Map.Entry<String, TreeMap<Integer, DatasetRow>> e : byClassId.entrySet()) {
            DatasetRow previous = null;
            for (DatasetRow current : e.getValue().values()) {
                if (previous != null) {
                    String where = e.getKey() + ": release " + previous.releaseId() + " -> " + current.releaseId();
                    if (number(current, nrHist) < number(previous, nrHist)) {
                        nrHistDown.add(where);
                    }
                    if (number(current, age) < number(previous, age)) {
                        ageDown.add(where);
                    }
                }
                previous = current;
            }
        }
        return List.of(untouched.result(), mergeBorn.result(), nrHistDown.result(), ageDown.result());
    }

    private static Map<String, DatasetRow> byKey(Dataset dataset) {
        Map<String, DatasetRow> map = new HashMap<>();
        dataset.rows().forEach(r -> map.put(r.releaseId() + "|" + r.path(), r));
        return map;
    }

    private static long number(DatasetRow row, int column) {
        return Long.parseLong(row.features().get(column));
    }

    private static String where(DatasetRow r) {
        return "release " + r.releaseId() + ", " + r.path();
    }
}