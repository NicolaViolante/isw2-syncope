package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.CsvWriter;
import it.uniroma2.control.DatasetBuilder;
import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.DatasetRow;
import it.uniroma2.entity.MetricsLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Fase 6 – Costruzione del CSV.
 *
 * <p>Unisce le feature (data/SYNCOPE_metrics.csv, Fase 4) e gli smell (data/SYNCOPE_smells.csv, Fase 5) in un
 * unico CSV con una riga per ogni coppia classe-release e Bugginess = "no" per tutte le righe. È il checkpoint da
 * cui parte il labeling (Fasi 7–9).
 *
 * <p>Output: output/SYNCOPE_dataset_nolabel.csv.
 */
public final class BuildDataset {

    private static final Logger LOGGER = Logger.getLogger(BuildDataset.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final Path DATA_DIR = Path.of("data");
    private static final Path OUTPUT_FILE = Path.of("output", PROJ_NAME + "_dataset_nolabel.csv");

    private BuildDataset() {
    }

    public static void main(String[] args) throws IOException {
        List<String> featureNames = new ArrayList<>();
        List<MetricsLine> metrics =
                CsvReader.readMetrics(DATA_DIR.resolve(PROJ_NAME + "_metrics.csv"), featureNames);
        Dataset dataset = DatasetBuilder.build(PROJ_NAME, featureNames, metrics,
                CsvReader.readSmells(DATA_DIR.resolve(PROJ_NAME + "_smells.csv")));
        CsvWriter.writeDataset(dataset, OUTPUT_FILE);

        long releases = dataset.rows().stream().mapToInt(DatasetRow::releaseId).distinct().count();
        long buggy = dataset.rows().stream().filter(DatasetRow::buggy).count();
        LOGGER.info(() -> String.format("Righe: %d | release: %d | feature: %d (+ NSmells) | Bugginess = yes: %d",
                dataset.rows().size(), releases, dataset.featureNames().size(), buggy));
    }
}
