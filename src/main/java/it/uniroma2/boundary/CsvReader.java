package it.uniroma2.boundary;

import it.uniroma2.entity.Release;

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
}