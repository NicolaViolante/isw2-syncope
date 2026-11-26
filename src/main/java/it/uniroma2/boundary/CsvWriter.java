package it.uniroma2.boundary;

import it.uniroma2.entity.ClassMetrics;
import it.uniroma2.entity.ClassSmells;
import it.uniroma2.entity.CodeMetrics;
import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.DatasetRow;
import it.uniroma2.entity.ProcessMetrics;
import it.uniroma2.entity.Release;
import it.uniroma2.entity.TicketFixes;
import it.uniroma2.entity.ReleaseClass;
import it.uniroma2.entity.Snapshot;
import it.uniroma2.entity.Ticket;
import it.uniroma2.entity.Ticket.JiraVersion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Boundary verso il file system: scrive i CSV prodotti dalle varie fasi. */
public final class CsvWriter {

    private static final String LIST_SEPARATOR = ";";

    private CsvWriter() {
    }

    /** Tutte le versioni di Jira, con il motivo di ogni scarto (Fase 1). */
    public static void writeAllReleases(List<Release> selected, Path file) throws IOException {
        try (PrintWriter out = open(file)) {
            out.println("Version ID,Version Name,Date,Line,Kept,Release ID,Discard Reason");
            for (Release r : selected) {
                out.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        r.jiraId(), r.name(),
                        r.date() == null ? "" : r.date(),
                        r.major() < 0 ? "" : r.line(),
                        r.isKept() ? "yes" : "no",
                        r.isKept() ? String.valueOf(r.seqId()) : "",
                        r.isKept() ? "" : r.discardReason());
            }
        }
    }

    /** La linea principale con gli ID 1, 2, 3… e la colonna InDataset (Fase 1). */
    public static void writeMainLine(List<Release> mainLine, int datasetSize, Path file) throws IOException {
        try (PrintWriter out = open(file)) {
            out.println("Release ID,Version ID,Version Name,Date,InDataset");
            for (Release r : mainLine) {
                out.printf("%d,%s,%s,%s,%s%n", r.seqId(), r.jiraId(), r.name(), r.date(),
                        r.seqId() <= datasetSize ? "yes" : "no");
            }
        }
    }

    /** I ticket di bug con AV e Fix Version; le versioni multiple sono separate da ";" (Fase 2). */
    public static void writeTickets(List<Ticket> tickets, Path file) throws IOException {
        try (PrintWriter out = open(file)) {
            out.println("Key,Created,Resolved,AV IDs,AV Names,FixVersion IDs,FixVersion Names");
            for (Ticket t : tickets) {
                out.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        t.key(),
                        t.created() == null ? "" : t.created(),
                        t.resolved() == null ? "" : t.resolved(),
                        join(t.affectedVersions(), JiraVersion::id),
                        join(t.affectedVersions(), JiraVersion::name),
                        join(t.fixVersions(), JiraVersion::id),
                        join(t.fixVersions(), JiraVersion::name));
            }
        }
    }

    /** Lo snapshot scelto per ogni release (Fase 3). */
    public static void writeSnapshots(List<Snapshot> snapshots, Map<Integer, Integer> classesByRelease, Path file)
            throws IOException {
        try (PrintWriter out = open(file)) {
            out.println("Release ID,Version Name,Release Date,Reference,Commit,Commit Date,From Tag,Classes");
            for (Snapshot s : snapshots) {
                Release r = s.release();
                out.printf("%d,%s,%s,%s,%s,%s,%s,%d%n", r.seqId(), r.name(), r.date(), s.reference(), s.commitId(),
                        s.commitDate(), s.fromTag() ? "yes" : "no", classesByRelease.getOrDefault(r.seqId(), 0));
            }
        }
    }

    /** L'inventario delle classi di produzione: una riga per ogni coppia classe-release (Fase 3). */
    public static void writeClasses(List<ReleaseClass> classes, Path file) throws IOException {
        try (PrintWriter out = open(file)) {
            out.println("Release ID,Class,Class ID");
            for (ReleaseClass c : classes) {
                out.printf("%d,%s,%s%n", c.releaseId(), c.path(), c.classId());
            }
        }
    }

    /** Numero di classi per modulo Maven e per release, per controllare l'inventario (Fase 3). */
    public static void writeModuleCounts(Map<String, Map<Integer, Integer>> countsByModule, List<Integer> releaseIds,
                                         Path file) throws IOException {
        try (PrintWriter out = open(file)) {
            out.println("Module," + releaseIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
            for (Map.Entry<String, Map<Integer, Integer>> e : countsByModule.entrySet()) {
                out.println(e.getKey() + "," + releaseIds.stream()
                        .map(id -> String.valueOf(e.getValue().getOrDefault(id, 0)))
                        .collect(Collectors.joining(",")));
            }
        }
    }

    /**
     * Le feature di ogni classe in ogni release (Fase 4). Le colonne "_Hist" sono le versioni
     * "dall'inizio"; le metriche di codice sono vuote se JavaParser non ha letto il file.
     */
    public static void writeMetrics(List<ClassMetrics> rows, Path file) throws IOException {
        try (PrintWriter out = open(file)) {
            out.println("Release ID,Class,Class ID,LOC,NOM,CyclomaticComplexity,FanOut,"
                    + "NR,NFix,NAuth,LOCAdded,MaxLOCAdded,Churn,MaxChurn,ChangeSetSize,MaxChangeSet,"
                    + "NR_Hist,NFix_Hist,NAuth_Hist,LOCAdded_Hist,ChangeSetSize_Hist,Age,WeightedAge");
            for (ClassMetrics r : rows) {
                CodeMetrics c = r.code();
                ProcessMetrics p = r.inRelease();
                ProcessMetrics h = r.history();
                out.printf(Locale.ROOT, "%d,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f%n",
                        r.releaseId(), r.path(), r.classId(),
                        c == null ? ",,," : c.loc() + "," + c.nom() + "," + c.cyclomaticComplexity() + "," + c.fanOut(),
                        p.nr(), p.nFix(), p.nAuth(), p.locAdded(), p.maxLocAdded(), p.churn(), p.maxChurn(),
                        p.changeSetSize(), p.maxChangeSet(),
                        h.nr(), h.nFix(), h.nAuth(), h.locAdded(), h.changeSetSize(),
                        r.age().age(), r.age().weightedAge());
            }
        }
    }

    /** NSmells di ogni classe (Fase 5); vuoto se SonarCloud non ha restituito il file. */
    public static void writeSmells(List<ClassSmells> rows, Path file) throws IOException {
        try (PrintWriter out = open(file)) {
            out.println("Release ID,Class,Class ID,NSmells");
            for (ClassSmells r : rows) {
                out.println(r.releaseId() + "," + r.path() + "," + r.classId() + ","
                        + (r.nSmells() == null ? "" : r.nSmells()));
            }
        }
    }

    /** Il dataset nel formato del prof: Project, Class, ReleaseID, feature, NSmells, Bugginess (yes/no). */
    public static void writeDataset(Dataset dataset, Path file) throws IOException {
        try (PrintWriter out = open(file)) {
            out.println("Project,Class,ReleaseID," + String.join(",", dataset.featureNames()) + ",NSmells,Bugginess");
            for (DatasetRow r : dataset.rows()) {
                out.println(r.project() + "," + r.path() + "," + r.releaseId() + "," + String.join(",", r.features())
                        + "," + r.nSmells() + "," + (r.buggy() ? "yes" : "no"));
            }
        }
    }

    /** I fix di ogni ticket (Fase 7); più valori nella stessa cella sono separati da ";". */
    public static void writeFixes(List<TicketFixes> fixes, Path file) throws IOException {
        try (PrintWriter out = open(file)) {
            out.println("Key,Category,Master Commits,Branch Commits,Class IDs,Other Java Files");
            for (TicketFixes f : fixes) {
                out.println(f.key() + "," + f.category() + "," + String.join(";", f.masterCommits()) + ","
                        + String.join(";", f.branchCommits()) + "," + String.join(";", new TreeSet<>(f.classIds()))
                        + "," + f.otherFiles());
            }
        }
    }

    private static PrintWriter open(Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        return new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
    }

    private static String join(List<JiraVersion> versions, Function<JiraVersion, String> field) {
        return versions.stream().map(field).collect(Collectors.joining(LIST_SEPARATOR));
    }
}