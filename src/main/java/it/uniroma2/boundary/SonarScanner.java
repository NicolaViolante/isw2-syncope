package it.uniroma2.boundary;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Lancia SonarScanner (da riga di comando) su una cartella che contiene solo le classi da analizzare.
 *
 * <p>La cartella non contiene pom.xml, test o file di altri linguaggi: lo scanner analizza esattamente quelle
 * classi e non prova a configurare Maven (che per alcune release del 2013 riuscirebbe e per altre no, rendendo
 * le analisi non confrontabili). La configurazione viene scritta in un file della cartella di lavoro e passata
 * con -Dproject.settings. Il token non passa da qui: SonarScanner lo legge da solo dalla variabile d'ambiente
 * SONAR_TOKEN.
 */
public final class SonarScanner {

    private static final String SONAR_HOST = "https://sonarcloud.io";
    /** Tentativi per release: un errore di rete momentaneo non deve fermare un'esecuzione di un'ora. */
    private static final int ATTEMPTS = 3;
    private static final long WAIT_BETWEEN_ATTEMPTS_MS = 30_000;
    /** Un'analisi normale dura meno di un minuto: oltre questo limite lo scanner è considerato bloccato. */
    private static final long SCANNER_TIMEOUT_MINUTES = 15;
    private static final List<String> KNOWN_LOCATIONS = List.of(
            "/opt/homebrew/bin/sonar-scanner", "/usr/local/bin/sonar-scanner");

    private final String organization;
    private final String projectKey;
    private final String javaSource;
    private final String executable;

    /**
     * @param javaSource versione di Java del codice analizzato (es. "1.6"), per non segnalare come smell il
     *                   mancato uso di costrutti che quella versione non aveva
     */
    public SonarScanner(String organization, String projectKey, String javaSource) {
        this.organization = organization;
        this.projectKey = projectKey;
        this.javaSource = javaSource;
        this.executable = findExecutable();
    }

    /** sonar-scanner: dalla variabile SONAR_SCANNER_PATH, dalle cartelle di Homebrew o dal PATH. */
    private static String findExecutable() {
        String configured = System.getenv("SONAR_SCANNER_PATH");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        for (String location : KNOWN_LOCATIONS) {
            if (new File(location).canExecute()) {
                return location;
            }
        }
        return "sonar-scanner";
    }

    /**
     * Analizza i file indicati e attende la fine dello scanner (non dell'elaborazione su SonarCloud).
     *
     * @param baseDir  cartella con le sole classi da analizzare
     * @param version  nome della release, usato come versione dell'analisi
     * @param workDir  cartella di lavoro nel nostro progetto
     * @return l'id del task di elaborazione su SonarCloud
     */
    public String analyze(File baseDir, String version, Path workDir)
            throws IOException, InterruptedException {
        Path scannerWork = workDir.resolve("scannerwork").toAbsolutePath();
        Path emptyBinaries = workDir.resolve("empty-binaries").toAbsolutePath();
        Files.createDirectories(scannerWork);
        Files.createDirectories(emptyBinaries);
        Files.deleteIfExists(scannerWork.resolve("report-task.txt"));

        Path settings = workDir.resolve("sonar-project.properties").toAbsolutePath();
        Properties p = new Properties();
        p.setProperty("sonar.host.url", SONAR_HOST);
        p.setProperty("sonar.organization", organization);
        p.setProperty("sonar.projectKey", projectKey);
        p.setProperty("sonar.projectVersion", version);
        p.setProperty("sonar.projectBaseDir", baseDir.getAbsolutePath());
        p.setProperty("sonar.working.directory", scannerWork.toString());
        p.setProperty("sonar.java.source", javaSource);
        p.setProperty("sonar.java.binaries", emptyBinaries.toString());
        p.setProperty("sonar.scm.disabled", "true");
        p.setProperty("sonar.sourceEncoding", "UTF-8");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(settings, StandardCharsets.UTF_8))) {
            p.store(out, "Generato da ComputeSmells per " + version);
        }

        Path log = workDir.resolve("scanner-" + version + ".log");
        int exit = -1;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Process process = new ProcessBuilder(executable, "-Dproject.settings=" + settings)
                    .directory(workDir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            if (!process.waitFor(SCANNER_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                exit = -1; // bloccato: si riprova
            } else {
                exit = process.waitFor();
            }
            if (exit == 0) {
                return readTaskId(scannerWork.resolve("report-task.txt"));
            }
            if (attempt < ATTEMPTS) {
                Thread.sleep(WAIT_BETWEEN_ATTEMPTS_MS);
            }
        }
        throw new IOException("SonarScanner terminato con codice " + exit + " dopo " + ATTEMPTS
                + " tentativi: vedi " + log.toAbsolutePath());
    }

    private static String readTaskId(Path reportTask) throws IOException {
        Properties report = new Properties();
        try (var in = Files.newBufferedReader(reportTask, StandardCharsets.UTF_8)) {
            report.load(in);
        }
        String id = report.getProperty("ceTaskId");
        if (id == null) {
            throw new IOException("ceTaskId mancante in " + reportTask);
        }
        return id;
    }
}