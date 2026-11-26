package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.CsvWriter;
import it.uniroma2.boundary.GitRepository;
import it.uniroma2.control.FixCommitFinder;
import it.uniroma2.control.FixLinker;
import it.uniroma2.entity.CommitData;
import it.uniroma2.entity.TicketFixes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Fase 7 – Commit di fix e linkage.
 *
 * <p>Cerca nei commit di tutti i rami (merge esclusi) quelli che citano i ticket bug della Fase 2, classifica ogni
 * ticket (A, B, C, D) e ricava le classi del dataset toccate dai suoi fix, come Class ID dell'inventario della
 * Fase 3. Il repository di Syncope viene solo letto.
 *
 * <p>Input in data/: SYNCOPE_tickets.csv, SYNCOPE_classes.csv. Argomento opzionale: percorso del fork di Syncope
 * (default ~/IdeaProjects/syncope). Output: output/SYNCOPE_fixes.csv.
 */
public final class LinkFixes {

    private static final Logger LOGGER = Logger.getLogger(LinkFixes.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Path DATA_DIR = Path.of("data");
    private static final Path OUTPUT_FILE = Path.of("output", PROJ_NAME + "_fixes.csv");

    private LinkFixes() {
    }

    public static void main(String[] args) throws IOException {
        File syncope = args.length > 0
                ? new File(args[0])
                : new File(System.getProperty("user.home"), "IdeaProjects/syncope");
        Set<String> bugKeys = CsvReader.readTicketKeys(DATA_DIR.resolve(PROJ_NAME + "_tickets.csv"));
        Map<String, String> classIdByPath = new HashMap<>();
        CsvReader.readClasses(DATA_DIR.resolve(PROJ_NAME + "_classes.csv"))
                .values().forEach(classIdByPath::putAll);

        List<TicketFixes> fixes;
        int allCommits;
        try (GitRepository git = new GitRepository(syncope)) {
            Map<String, String> messages = git.allCommitMessages();
            allCommits = messages.size();
            Map<String, List<String>> byTicket =
                    FixLinker.commitsByTicket(messages, new FixCommitFinder(PROJ_NAME, bugKeys));
            Map<String, CommitData> commits = new HashMap<>();
            for (List<String> ids : byTicket.values()) {
                for (String id : ids) {
                    if (!commits.containsKey(id)) {
                        commits.put(id, git.commitData(id));
                    }
                }
            }
            fixes = FixLinker.link(bugKeys, byTicket, git.commitsOnMaster(), commits, classIdByPath);
            LOGGER.info(String.format("Commit (tutti i rami, merge esclusi): %d | commit di fix: %d",
                    allCommits, commits.size()));
        }
        CsvWriter.writeFixes(fixes, OUTPUT_FILE);

        Map<TicketFixes.Category, Integer> byCategory = new EnumMap<>(TicketFixes.Category.class);
        fixes.forEach(f -> byCategory.merge(f.category(), 1, Integer::sum));
        long withClasses = fixes.stream().filter(f -> !f.classIds().isEmpty()).count();
        long onlyOutside = fixes.stream().filter(f -> f.classIds().isEmpty() && f.otherFiles() > 0).count();
        long noJava = fixes.stream()
                .filter(f -> f.category() != TicketFixes.Category.D && f.classIds().isEmpty() && f.otherFiles() == 0)
                .count();
        LOGGER.info(() -> String.format("Ticket: %d | per categoria: %s | con classi del dataset: %d | "
                        + "solo classi fuori dal dataset: %d | fix senza file Java di produzione: %d",
                fixes.size(), byCategory, withClasses, onlyOutside, noJava));
    }
}