package it.uniroma2.boundary;

import it.uniroma2.entity.ClassSmells;
import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.DatasetRow;
import it.uniroma2.entity.MetricsLine;
import it.uniroma2.entity.Release;
import it.uniroma2.entity.Ticket;
import it.uniroma2.entity.TicketFixes;
import it.uniroma2.entity.TicketVersions;
import it.uniroma2.entity.Ticket;
import it.uniroma2.entity.TicketFixes;
import it.uniroma2.entity.TicketVersions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Boundary verso il file system: legge i CSV congelati in data/ dalle fasi precedenti. */
public final class CsvReader {

    private CsvReader() {
    }

    /**
     * Legge la linea principale (data/SYNCOPE_releases.csv, prodotto dalla Fase 1).
     *
     * @param onlyDataset se true restituisce solo le release con InDataset = yes
     */
    public static List<Release> readMainLine(Path file, boolean onlyDataset) throws IOException {
        List<Release> releases = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        // intestazione: Release ID,Version ID,Version Name,Date,InDataset
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            if (onlyDataset && !"yes".equals(f[4])) {
                continue;
            }
            Release r = Release.fromJira(f[1], f[2], LocalDate.parse(f[3]));
            releases.add(r.withSeqId(Integer.parseInt(f[0])));
        }
        return releases;
    }

    /**
     * Legge l'inventario delle classi (data/SYNCOPE_classes.csv, prodotto dalla Fase 3).
     *
     * @return per ogni Release ID, i percorsi delle classi con il loro Class ID, nell'ordine del file
     */
    public static Map<Integer, Map<String, String>> readClasses(Path file) throws IOException {
        Map<Integer, Map<String, String>> classes = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        // intestazione: Release ID,Class,Class ID
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            classes.computeIfAbsent(Integer.parseInt(f[0]), k -> new LinkedHashMap<>()).put(f[1], f[2]);
        }
        return classes;
    }

    /** Le chiavi dei ticket bug (prima colonna di data/SYNCOPE_tickets.csv, prodotto dalla Fase 2). */
    public static Set<String> readTicketKeys(Path file) throws IOException {
        Set<String> keys = new HashSet<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            if (!line.isBlank()) {
                keys.add(line.substring(0, line.indexOf(',')));
            }
        }
        return keys;
    }

    /** Rilegge un file scritto da CsvWriter.writeSmells (per riprendere un'esecuzione interrotta). */
    public static List<ClassSmells> readSmells(Path file) throws IOException {
        List<ClassSmells> rows = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            rows.add(new ClassSmells(Integer.parseInt(f[0]), f[1], f[2], f[3].isEmpty() ? null : Integer.valueOf(f[3])));
        }
        return rows;
    }

    /**
     * Legge le metriche della Fase 4 (data/SYNCOPE_metrics.csv). Le prime tre colonne sono Release ID, Class e
     * Class ID; tutte le altre sono feature e vengono lette come testo, senza cambiarne il formato.
     *
     * @param featureNames lista che viene riempita con i nomi delle feature, nell'ordine delle colonne
     */
    public static List<MetricsLine> readMetrics(Path file, List<String> featureNames)
            throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String[] header = lines.get(0).split(",", -1);
        featureNames.addAll(List.of(header).subList(3, header.length));
        List<MetricsLine> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            rows.add(new MetricsLine(Integer.parseInt(f[0]), f[1], List.of(f).subList(3, f.length)));
        }
        return rows;
    }

    /** Tutte le versioni di Jira con l'esito della Fase 1 (data/SYNCOPE_releases_all.csv). */
    public static List<Release> readAllReleases(Path file) throws IOException {
        List<Release> releases = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        // intestazione: Version ID,Version Name,Date,Line,Kept,Release ID,Discard Reason
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            LocalDate date = f[2].isEmpty() ? null : LocalDate.parse(f[2]);
            Release r = Release.fromJira(f[0], f[1], date);
            releases.add("yes".equals(f[4]) ? r.withSeqId(Integer.parseInt(f[5])) : r.discarded(f[6]));
        }
        return releases;
    }

    /** I ticket bug della Fase 2 (data/SYNCOPE_tickets.csv); più versioni nella stessa cella separate da ";". */
    public static List<Ticket> readTickets(Path file) throws IOException {
        List<Ticket> tickets = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        // intestazione: Key,Created,Resolved,AV IDs,AV Names,FixVersion IDs,FixVersion Names
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            tickets.add(new Ticket(f[0], f[1].isEmpty() ? null : LocalDate.parse(f[1]),
                    f[2].isEmpty() ? null : LocalDate.parse(f[2]), versions(f[3], f[4]), versions(f[5], f[6])));
        }
        return tickets;
    }

    private static List<Ticket.JiraVersion> versions(String ids, String names) {
        List<Ticket.JiraVersion> versions = new ArrayList<>();
        if (ids.isEmpty()) {
            return versions;
        }
        String[] i = ids.split(";");
        String[] n = names.split(";");
        for (int k = 0; k < i.length; k++) {
            versions.add(new Ticket.JiraVersion(i[k], k < n.length ? n[k] : ""));
        }
        return versions;
    }

    /** Rilegge un dataset scritto da CsvWriter.writeDataset (Project, Class, ReleaseID, feature, NSmells, Bugginess). */
    public static Dataset readDataset(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String[] header = lines.get(0).split(",", -1);
        List<String> featureNames = List.of(header).subList(3, header.length - 2);
        List<DatasetRow> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            rows.add(new DatasetRow(f[0], Integer.parseInt(f[2]), f[1], List.of(f).subList(3, f.length - 2),
                    Integer.parseInt(f[f.length - 2]), "yes".equals(f[f.length - 1])));
        }
        return new Dataset(featureNames, rows);
    }

    /** Rilegge le classi toccate da ogni ticket (data/SYNCOPE_fixes.csv, Fase 7). */
    public static List<TicketFixes> readFixes(Path file) throws IOException {
        List<TicketFixes> fixes = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        // intestazione: Key,Category,Master Commits,Branch Commits,Class IDs,Other Java Files
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            fixes.add(new TicketFixes(f[0], TicketFixes.Category.valueOf(f[1]), split(f[2]), split(f[3]),
                    new HashSet<>(split(f[4])), Integer.parseInt(f[5])));
        }
        return fixes;
    }

    /** Rilegge OV, FV e IV di ogni ticket (data/SYNCOPE_versions.csv, Fase 8). */
    public static List<TicketVersions> readVersions(Path file) throws IOException {
        List<TicketVersions> versions = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        // intestazione: Key,OV,FV,IV,IV Source
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            versions.add(new TicketVersions(f[0], integer(f[1]), integer(f[2]), integer(f[3]),
                    TicketVersions.Source.valueOf(f[4])));
        }
        return versions;
    }

    private static List<String> split(String cell) {
        return cell.isEmpty() ? List.of() : List.of(cell.split(";"));
    }

    private static Integer integer(String cell) {
        return cell.isEmpty() ? null : Integer.valueOf(cell);
    }
}