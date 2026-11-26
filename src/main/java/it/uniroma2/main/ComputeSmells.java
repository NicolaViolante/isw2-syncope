package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.CsvWriter;
import it.uniroma2.boundary.GitRepository;
import it.uniroma2.boundary.SonarCloudClient;
import it.uniroma2.boundary.SonarScanner;
import it.uniroma2.control.SmellAssigner;
import it.uniroma2.entity.ClassSmells;
import it.uniroma2.entity.Release;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Fase 5 – Smell.
 *
 * <p>Per ogni release del dataset: le sole classi dell'inventario (Fase 3) vengono copiate dal tag in una
 * cartella pulita (output/sonar/src), analizzate con SonarScanner sul progetto SonarCloud dedicato e, finita
 * l'elaborazione, viene letto il numero di code smell per file. Il fork viene solo letto. Il risultato di
 * ogni release viene salvato subito: se l'esecuzione si interrompe, rilanciandola le release già fatte vengono
 * saltate.
 *
 * <p>Richiede la variabile d'ambiente SONAR_TOKEN (impostata nella configurazione di esecuzione, mai nel
 * codice). Argomento opzionale: percorso del fork di Syncope (default ~/IdeaProjects/syncope).
 *
 * <p>Output: output/SYNCOPE_smells.csv; file di lavoro e log dello scanner in output/sonar/.
 */
public final class ComputeSmells {

    private static final Logger LOGGER = Logger.getLogger(ComputeSmells.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final String ORGANIZATION = "nicolaviolante";
    private static final String PROJECT_KEY = "NicolaViolante_syncope-smells";
    /** Syncope 1.x è scritto per Java 6 (targetJdk 1.6 nei pom dei tag). */
    private static final String JAVA_SOURCE = "1.6";
    private static final Path DATA_DIR = Path.of("data");
    private static final Path WORK_DIR = Path.of("output", "sonar");
    private static final Path OUTPUT_FILE = Path.of("output", PROJ_NAME + "_smells.csv");

    private ComputeSmells() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String token = System.getenv("SONAR_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Variabile d'ambiente SONAR_TOKEN non impostata "
                    + "(Run > Edit Configurations > Environment variables)");
        }
        File syncope = args.length > 0
                ? new File(args[0])
                : new File(System.getProperty("user.home"), "IdeaProjects/syncope");
        List<Release> releases = CsvReader.readMainLine(DATA_DIR.resolve(PROJ_NAME + "_releases.csv"), true);
        Map<Integer, Map<String, String>> classes =
                CsvReader.readClasses(DATA_DIR.resolve(PROJ_NAME + "_classes.csv"));
        Files.createDirectories(WORK_DIR);

        SonarScanner scanner = new SonarScanner(ORGANIZATION, PROJECT_KEY, JAVA_SOURCE);
        SonarCloudClient sonar = new SonarCloudClient(SonarCloudClient.SONARCLOUD, token);
        List<ClassSmells> all = new ArrayList<>();

        Path sources = WORK_DIR.resolve("src");
        try (GitRepository git = new GitRepository(syncope)) {
            for (Release release : releases) {
                Path done = WORK_DIR.resolve("smells-" + release.seqId() + ".csv");
                if (Files.exists(done)) {
                    all.addAll(CsvReader.readSmells(done));
                    LOGGER.info(() -> release.name() + ": già analizzata, letta da " + done);
                    continue;
                }
                Map<String, String> atTag = classes.get(release.seqId());
                long start = System.nanoTime();
                git.exportFiles(git.snapshotOf(release), atTag.keySet(), sources);
                String taskId = scanner.analyze(sources.toFile(), release.name(), WORK_DIR);
                sonar.waitForTask(taskId);
                List<ClassSmells> rows = SmellAssigner.assign(release.seqId(), atTag, sonar.fileSmells(PROJECT_KEY));
                CsvWriter.writeSmells(rows, done);
                all.addAll(rows);
                long missing = rows.stream().filter(r -> r.nSmells() == null).count();
                int total = rows.stream().filter(r -> r.nSmells() != null).mapToInt(ClassSmells::nSmells).sum();
                long seconds = (System.nanoTime() - start) / 1_000_000_000L;
                LOGGER.info(() -> String.format("%-18s classi: %d | smell totali: %d | file non restituiti: %d | %d s",
                        release.name(), rows.size(), total, missing, seconds));
            }
        }

        CsvWriter.writeSmells(all, OUTPUT_FILE);
        long missing = all.stream().filter(r -> r.nSmells() == null).count();
        long withSmells = all.stream().filter(r -> r.nSmells() != null && r.nSmells() > 0).count();
        LOGGER.info(() -> String.format("Righe: %d | con almeno uno smell: %d | file non restituiti da SonarCloud: %d",
                all.size(), withSmells, missing));
    }
}