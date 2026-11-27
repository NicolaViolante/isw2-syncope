package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.CsvWriter;
import it.uniroma2.boundary.GitRepository;
import it.uniroma2.control.FixVersionFinder;
import it.uniroma2.control.TicketVersionResolver;
import it.uniroma2.entity.Release;
import it.uniroma2.entity.TicketFixes;
import it.uniroma2.entity.TicketVersions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Fase 8 – OV, FV, IV e Proportion.
 *
 * <p>Per ogni ticket bug della Fase 2 calcola Opening, Fix e Injected Version sulla linea principale della Fase 1.
 * L'OV viene dalla data di apertura (un evento senza codice); la FV dal codice: la prima release dopo l'OV il cui tag
 * contiene un commit di fix della Fase 7; la IV dalla prima Affected Version coerente oppure da Proportion Total.
 *
 * <p>Input in data/: SYNCOPE_releases_all.csv, SYNCOPE_tickets.csv, SYNCOPE_fixes.csv. Argomento opzionale: percorso
 * del fork di Syncope (default ~/IdeaProjects/syncope), solo letto. Output: output/SYNCOPE_versions.csv.
 */
public final class ComputeVersions {

    private static final Logger LOGGER = Logger.getLogger(ComputeVersions.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Path DATA_DIR = Path.of("data");
    private static final Path OUTPUT_FILE = Path.of("output", PROJ_NAME + "_versions.csv");

    private ComputeVersions() {
    }

    public static void main(String[] args) throws IOException {
        File syncope = args.length > 0
                ? new File(args[0])
                : new File(System.getProperty("user.home"), "IdeaProjects/syncope");

        List<Release> allVersions = CsvReader.readAllReleases(DATA_DIR.resolve(PROJ_NAME + "_releases_all.csv"));
        List<TicketFixes> fixes = CsvReader.readFixes(DATA_DIR.resolve(PROJ_NAME + "_fixes.csv"));
        Map<String, List<String>> fixCommits = new HashMap<>();
        Set<String> allFixCommits = new HashSet<>();
        for (TicketFixes f : fixes) {
            List<String> commits = new ArrayList<>(f.masterCommits());
            commits.addAll(f.branchCommits());
            fixCommits.put(f.key(), commits);
            allFixCommits.addAll(commits);
        }

        // Per ogni release della linea principale, i commit di fix contenuti nel suo tag
        Map<Integer, Set<String>> fixCommitsByRelease = new HashMap<>();
        try (GitRepository git = new GitRepository(syncope)) {
            for (Release release : allVersions) {
                if (!release.isKept()) {
                    continue;
                }
                Set<String> contained = new HashSet<>(git.historyOf(git.snapshotOf(release)));
                contained.retainAll(allFixCommits);
                fixCommitsByRelease.put(release.seqId(), contained);
            }
        }

        TicketVersionResolver resolver = new TicketVersionResolver(allVersions,
                new FixVersionFinder(fixCommitsByRelease));
        List<TicketVersions> versions =
                resolver.resolve(CsvReader.readTickets(DATA_DIR.resolve(PROJ_NAME + "_tickets.csv")), fixCommits);
        CsvWriter.writeVersions(versions, OUTPUT_FILE);

        Map<TicketVersions.Source, Integer> bySource = new EnumMap<>(TicketVersions.Source.class);
        versions.forEach(v -> bySource.merge(v.source(), 1, Integer::sum));
        long used = versions.stream().filter(v -> !v.isExcluded()).count();
        long estimated = used - bySource.getOrDefault(TicketVersions.Source.AV, 0);
        LOGGER.info(() -> String.format(Locale.ROOT, "Release della linea principale lette dai tag: %d",
                fixCommitsByRelease.size()));
        LOGGER.info(() -> String.format(Locale.ROOT, "Ticket: %d | per origine della IV: %s", versions.size(), bySource));
        LOGGER.info(() -> String.format(Locale.ROOT, "P = %.4f (su %d ticket con AV coerenti) | IV da Proportion: %d su %d "
                        + "ticket usati (%.1f%%)", resolver.proportion(), resolver.proportionTickets(), estimated, used,
                100.0 * estimated / used));
    }
}