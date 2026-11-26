package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.CsvWriter;
import it.uniroma2.control.Labeler;
import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.DatasetRow;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Fase 9 – Labeling.
 *
 * <p>Parte dal dataset della Fase 6 (tutte le righe "no") e accende "yes" sulle classi toccate dai fix di ogni ticket
 * nelle release da IV inclusa a FV esclusa, seguendo il Class ID.
 *
 * <p>Input in data/: SYNCOPE_dataset_nolabel.csv, SYNCOPE_classes.csv, SYNCOPE_fixes.csv, SYNCOPE_versions.csv.
 * Output: output/SYNCOPE_dataset.csv (il dataset finale della Milestone 1).
 */
public final class LabelDataset {

    private static final Logger LOGGER = Logger.getLogger(LabelDataset.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Path DATA_DIR = Path.of("data");
    private static final Path OUTPUT_FILE = Path.of("output", PROJ_NAME + "_dataset.csv");

    private LabelDataset() {
    }

    public static void main(String[] args) throws IOException {
        Labeler labeler = new Labeler();
        Dataset labeled = labeler.label(
                CsvReader.readDataset(DATA_DIR.resolve(PROJ_NAME + "_dataset_nolabel.csv")),
                CsvReader.readClasses(DATA_DIR.resolve(PROJ_NAME + "_classes.csv")),
                CsvReader.readFixes(DATA_DIR.resolve(PROJ_NAME + "_fixes.csv")),
                CsvReader.readVersions(DATA_DIR.resolve(PROJ_NAME + "_versions.csv")));
        CsvWriter.writeDataset(labeled, OUTPUT_FILE);

        Map<Integer, int[]> byRelease = new TreeMap<>(); // releaseId → {righe, buggy}
        for (DatasetRow r : labeled.rows()) {
            int[] c = byRelease.computeIfAbsent(r.releaseId(), k -> new int[2]);
            c[0]++;
            if (r.buggy()) {
                c[1]++;
            }
        }
        StringBuilder perRelease = new StringBuilder();
        byRelease.forEach((id, c) -> perRelease.append(String.format(Locale.ROOT, " %d:%.1f%%", id, 100.0 * c[1] / c[0])));
        List<DatasetRow> rows = labeled.rows();
        long buggy = rows.stream().filter(DatasetRow::buggy).count();
        LOGGER.info(() -> String.format(Locale.ROOT, "Righe: %d | buggy: %d (%.2f%%) | ticket che segnano almeno una riga: %d",
                rows.size(), buggy, 100.0 * buggy / rows.size(), labeler.contributingTickets()));
        LOGGER.info(() -> "Buggy per release:" + perRelease);
    }
}