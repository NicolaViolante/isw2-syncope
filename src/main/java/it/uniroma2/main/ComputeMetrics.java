package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.CsvWriter;
import it.uniroma2.boundary.GitRepository;
import it.uniroma2.control.AgeCalculator;
import it.uniroma2.control.CodeMetricsCalculator;
import it.uniroma2.control.FixCommitFinder;
import it.uniroma2.control.HistoryAttributor;
import it.uniroma2.control.ProcessMetricsCalculator;
import it.uniroma2.entity.AgeMetrics;
import it.uniroma2.entity.ClassMetrics;
import it.uniroma2.entity.CodeMetrics;
import it.uniroma2.entity.CommitData;
import it.uniroma2.entity.Release;
import it.uniroma2.entity.Snapshot;
import it.uniroma2.entity.Touch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Fase 4 – Metriche.
 *
 * <p>Per ogni release del dataset e ogni classe dell'inventario della Fase 3 calcola le 20 feature:
 * <ul>
 *   <li>metriche di codice con JavaParser sul file nello snapshot (tag);</li>
 *   <li>metriche di processo sui commit della release (contenuti nel tag k ma non nel tag k−1) e su tutti
 *       i commit contenuti nel tag ("dall'inizio"), merge esclusi;</li>
 *   <li>Age e WeightedAge in settimane fino alla data di rilascio di Jira.</li>
 * </ul>
 * Input congelati in data/: SYNCOPE_releases.csv (Fase 1), SYNCOPE_tickets.csv (Fase 2),
 * SYNCOPE_classes.csv (Fase 3). Il repository di Syncope viene solo letto.
 *
 * <p>Argomento opzionale: percorso della copia locale di Syncope (default ~/IdeaProjects/syncope).
 *
 * <p>Output: output/SYNCOPE_metrics.csv.
 */
public final class ComputeMetrics {

    private static final Logger LOGGER = Logger.getLogger(ComputeMetrics.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Path DATA_DIR = Path.of("data");
    private static final Path OUTPUT_FILE = Path.of("output", PROJ_NAME + "_metrics.csv");

    private ComputeMetrics() {
    }

    public static void main(String[] args) throws IOException {
        File syncope = args.length > 0
                ? new File(args[0])
                : new File(System.getProperty("user.home"), "IdeaProjects/syncope");
        List<Release> releases = CsvReader.readMainLine(DATA_DIR.resolve(PROJ_NAME + "_releases.csv"), true);
        Map<Integer, Map<String, String>> classes =
                CsvReader.readClasses(DATA_DIR.resolve(PROJ_NAME + "_classes.csv"));
        Set<String> bugKeys = CsvReader.readTicketKeys(DATA_DIR.resolve(PROJ_NAME + "_tickets.csv"));

        ProcessMetricsCalculator process = new ProcessMetricsCalculator(new FixCommitFinder(PROJ_NAME, bugKeys));
        CodeMetricsCalculator code = new CodeMetricsCalculator();
        HistoryAttributor attributor = new HistoryAttributor();

        List<ClassMetrics> rows = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        List<String> bornInMerge = new ArrayList<>();
        int lastRenames = 0;
        try (GitRepository git = new GitRepository(syncope)) {
            Set<String> previousHistory = Set.of();
            for (Release release : releases) {
                Snapshot snapshot = git.snapshotOf(release);
                List<String> historyIds = git.historyOf(snapshot);
                Set<String> inRelease = new HashSet<>(historyIds);
                inRelease.removeAll(previousHistory);
                List<CommitData> history = new ArrayList<>();
                for (String id : historyIds) {
                    history.add(git.commitData(id));
                }

                Map<String, String> atTag = classes.get(release.seqId());
                Map<String, List<Touch>> touches = attributor.attribute(history, inRelease, atTag.keySet());
                for (Map.Entry<String, String> c : atTag.entrySet()) {
                    List<Touch> all = touches.get(c.getKey());
                    List<Touch> window = all.stream().filter(Touch::inRelease).toList();
                    AgeMetrics age;
                    if (all.isEmpty()) {
                        // nessun commit (merge esclusi) l'ha toccata: la classe è nata in un merge
                        bornInMerge.add(release.name() + " " + c.getKey());
                        age = AgeCalculator.fromCreation(git.firstCommitDay(snapshot, c.getKey()), release.date());
                    } else {
                        age = AgeCalculator.compute(all, release.date());
                    }
                    CodeMetrics codeMetrics = code.compute(git.readFile(snapshot, c.getKey())).orElse(null);
                    if (codeMetrics == null) {
                        unreadable.add(release.name() + " " + c.getKey());
                    }
                    rows.add(new ClassMetrics(release.seqId(), c.getKey(), c.getValue(), codeMetrics,
                            process.compute(window), process.compute(all), age));
                }
                LOGGER.info(String.format("%-18s commit nella release: %5d | dall'inizio: %5d | classi: %d",
                        release.name(), inRelease.size(), historyIds.size(), atTag.size()));
                previousHistory = new HashSet<>(historyIds);
                lastRenames = attributor.renamesFollowed();
            }
        }

        CsvWriter.writeMetrics(rows, OUTPUT_FILE);
        unreadable.forEach(f -> LOGGER.warning("JavaParser non ha letto: " + f));
        bornInMerge.forEach(f -> LOGGER.info("Classe nata in un commit di merge: " + f));
        int renames = lastRenames;
        LOGGER.info(() -> String.format(
                "Righe: %d | file non letti da JavaParser: %d | righe di classi nate in un merge: %d | "
                        + "rinomine seguite nella storia dell'ultima release: %d",
                rows.size(), unreadable.size(), bornInMerge.size(), renames));
    }
}