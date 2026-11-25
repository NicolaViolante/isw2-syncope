package it.uniroma2.boundary;

import it.uniroma2.entity.Release;
import it.uniroma2.entity.Ticket;
import it.uniroma2.entity.Ticket.JiraVersion;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
