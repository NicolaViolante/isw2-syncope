package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.CsvWriter;
import it.uniroma2.boundary.GitRepository;
import it.uniroma2.control.DatasetChecker;
import it.uniroma2.control.FixCommitFinder;
import it.uniroma2.control.Labeler;
import it.uniroma2.control.LinkageCalculator;
import it.uniroma2.control.ReportStatistics;
import it.uniroma2.entity.CheckResult;
import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.DatasetRow;
import it.uniroma2.entity.Linkage;
import it.uniroma2.entity.Release;
import it.uniroma2.entity.ReleaseSummary;
import it.uniroma2.entity.Ticket;
import it.uniroma2.entity.TicketFixes;
import it.uniroma2.entity.TicketVersions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Fase 10 – Controlli sul dataset, linkage e numeri per il report.
 *
 * <p>Non produce nuovi dati: rilegge i file congelati delle fasi precedenti, controlla il dataset finale, calcola il
 * linkage ticket ↔ commit e stampa i numeri da riportare. Il repository di Syncope viene solo letto.
 *
 * <p>Input in data/: SYNCOPE_dataset.csv, SYNCOPE_dataset_nolabel.csv, SYNCOPE_classes.csv, SYNCOPE_releases.csv,
 * SYNCOPE_releases_all.csv, SYNCOPE_tickets.csv, SYNCOPE_fixes.csv, SYNCOPE_versions.csv. Argomento opzionale:
 * percorso del fork di Syncope (default ~/IdeaProjects/syncope). Output: output/SYNCOPE_release_summary.csv.
 */
public final class CheckDataset {

    private static final Logger LOGGER = Logger.getLogger(CheckDataset.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Path DATA_DIR = Path.of("data");
    private static final Path OUTPUT_FILE = Path.of("output", PROJ_NAME + "_release_summary.csv");

    private CheckDataset() {
    }

    public static void main(String[] args) throws IOException {
        File syncope = args.length > 0
                ? new File(args[0])
                : new File(System.getProperty("user.home"), "IdeaProjects/syncope");

        Dataset dataset = CsvReader.readDataset(data("_dataset.csv"));
        Dataset unlabeled = CsvReader.readDataset(data("_dataset_nolabel.csv"));
        Map<Integer, Map<String, String>> classes = CsvReader.readClasses(data("_classes.csv"));
        List<TicketFixes> fixes = CsvReader.readFixes(data("_fixes.csv"));
        List<TicketVersions> versions = CsvReader.readVersions(data("_versions.csv"));
        List<Ticket> tickets = CsvReader.readTickets(data("_tickets.csv"));

        // 1. Controlli
        Labeler labeler = new Labeler();
        Dataset relabeled = labeler.label(unlabeled, classes, fixes, versions);
        List<CheckResult> checks = new DatasetChecker().check(dataset, unlabeled, relabeled, classes, PROJ_NAME);
        LOGGER.info("=== Controlli sul dataset ===");
        for (CheckResult c : checks) {
            String status;
            if (!c.blocking()) {
                status = "INFO";
            } else {
                status = c.passed() ? "OK" : "ERRORE";
            }
            String example = c.count() == 0 ? "" : " (es. " + c.example() + ")";
            String count = c.blocking() && c.passed() ? "" : ": " + c.count();
            LOGGER.info(() -> String.format("[%s] %s%s%s", status, c.description(), count, example));
        }
        long failed = checks.stream().filter(c -> !c.passed()).count();
        if (failed == 0) {
            LOGGER.info("Tutti i controlli bloccanti superati.");
        } else {
            LOGGER.severe(() -> "Controlli falliti: " + failed);
        }

        // 2. Linkage
        Linkage linkage;
        try (GitRepository git = new GitRepository(syncope)) {
            linkage = LinkageCalculator.compute(fixes, versions, git.allCommitMessages(),
                    new FixCommitFinder(PROJ_NAME, CsvReader.readTicketKeys(data("_tickets.csv"))));
        }
        LOGGER.info("=== Linkage ===");
        LOGGER.info(() -> String.format(Locale.ROOT, "Ticket bug con almeno un commit: %d su %d (%.1f%%)",
                linkage.ticketsWithCommits(), linkage.bugTickets(),
                Linkage.percent(linkage.ticketsWithCommits(), linkage.bugTickets())));
        LOGGER.info(() -> String.format(Locale.ROOT, "Ticket usati (con OV e FV): %d, di cui con commit: %d (la FV viene "
                        + "dal codice, quindi un ticket usato ha sempre un commit di fix)", linkage.usedTickets(),
                linkage.usedWithCommits()));
        LOGGER.info(() -> String.format(Locale.ROOT, "Ticket bug i cui fix toccano classi del dataset: %d su %d (%.1f%%)",
                linkage.ticketsWithClasses(), linkage.bugTickets(),
                Linkage.percent(linkage.ticketsWithClasses(), linkage.bugTickets())));
        LOGGER.info(() -> String.format(Locale.ROOT, "Commit che citano un ticket bug: %d su %d (%.1f%%)",
                linkage.commitsCitingBug(), linkage.commits(),
                Linkage.percent(linkage.commitsCitingBug(), linkage.commits())));
        LOGGER.info(() -> String.format(Locale.ROOT, "Commit che citano un ticket qualsiasi: %d su %d (%.1f%%)",
                linkage.commitsCitingTicket(), linkage.commits(),
                Linkage.percent(linkage.commitsCitingTicket(), linkage.commits())));

        // 3. Numeri per il report
        logReleases();
        logTickets(tickets, fixes, versions);
        logDataset(dataset, classes, labeler.contributingTickets());
    }

    private static void logReleases() throws IOException {
        List<Release> all = CsvReader.readAllReleases(data("_releases_all.csv"));
        Map<String, Integer> byReason = new TreeMap<>();
        all.stream().filter(r -> !r.isKept()).forEach(r -> byReason.merge(r.discardReason(), 1, Integer::sum));
        int mainLine = CsvReader.readMainLine(data("_releases.csv"), false).size();
        int inDataset = CsvReader.readMainLine(data("_releases.csv"), true).size();
        LOGGER.info("=== Release ===");
        LOGGER.info(() -> String.format("Versioni su Jira: %d | linea principale: %d | nel dataset: %d",
                all.size(), mainLine, inDataset));
        LOGGER.info(() -> "Scartate per motivo: " + byReason);
    }

    private static void logTickets(List<Ticket> tickets, List<TicketFixes> fixes, List<TicketVersions> versions) {
        long withAv = tickets.stream().filter(Ticket::hasAffectedVersions).count();
        Map<TicketFixes.Category, Integer> byCategory = new EnumMap<>(TicketFixes.Category.class);
        fixes.forEach(f -> byCategory.merge(f.category(), 1, Integer::sum));
        Map<TicketVersions.Source, Integer> bySource = new EnumMap<>(TicketVersions.Source.class);
        versions.forEach(v -> bySource.merge(v.source(), 1, Integer::sum));
        double[] p = ReportStatistics.proportion(versions);
        LOGGER.info("=== Ticket ===");
        LOGGER.info(() -> String.format("Ticket bug: %d | con AV su Jira: %d | per categoria: %s",
                tickets.size(), withAv, byCategory));
        LOGGER.info(() -> "Per origine della IV: " + bySource);
        LOGGER.info(() -> String.format(Locale.ROOT, "P ricalcolato dal file della Fase 8: %.4f (su %d ticket)",
                p[0], (int) p[1]));
    }

    private static void logDataset(Dataset dataset, Map<Integer, Map<String, String>> classes, int contributing)
            throws IOException {
        List<DatasetRow> rows = dataset.rows();
        long buggy = rows.stream().filter(DatasetRow::buggy).count();
        Set<String> classIds = new HashSet<>();
        Set<String> paths = new HashSet<>();
        Set<String> buggyIds = new HashSet<>();
        for (DatasetRow r : rows) {
            String id = classes.get(r.releaseId()).get(r.path());
            classIds.add(id);
            paths.add(r.path());
            if (r.buggy()) {
                buggyIds.add(id);
            }
        }
        long withSmells = rows.stream().filter(r -> r.nSmells() > 0).count();
        long smells = rows.stream().mapToLong(DatasetRow::nSmells).sum();
        List<ReleaseSummary> summaries = ReportStatistics.byRelease(dataset,
                CsvReader.readMainLine(data("_releases.csv"), true));
        CsvWriter.writeReleaseSummary(summaries, OUTPUT_FILE);

        LOGGER.info("=== Dataset ===");
        LOGGER.info(() -> String.format(Locale.ROOT, "Righe: %d | Class ID distinti: %d | percorsi distinti: %d",
                rows.size(), classIds.size(), paths.size()));
        LOGGER.info(() -> String.format(Locale.ROOT, "Buggy: %d (%.2f%%) | classi buggy almeno una volta: %d | "
                        + "ticket che segnano almeno una riga: %d", buggy, 100.0 * buggy / rows.size(), buggyIds.size(),
                contributing));
        LOGGER.info(() -> String.format(Locale.ROOT, "NSmells: %d righe con almeno uno smell (%.1f%%), %d smell in "
                        + "totale, media %.2f per riga", withSmells, 100.0 * withSmells / rows.size(), smells,
                (double) smells / rows.size()));
        StringBuilder perRelease = new StringBuilder();
        summaries.forEach(s -> perRelease.append(String.format(Locale.ROOT, "%n  %2d %-18s classi %4d | toccate %4d | "
                        + "buggy %4d (%5.1f%%) | smell %5d", s.releaseId(), s.version(), s.classes(), s.touched(), s.buggy(),
                s.buggyPercent(), s.nSmells())));
        LOGGER.info(() -> "Per release:" + perRelease);
    }

    private static Path data(String suffix) {
        return DATA_DIR.resolve(PROJ_NAME + suffix);
    }
}