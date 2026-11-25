package it.uniroma2.boundary;

import it.uniroma2.entity.Release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
}