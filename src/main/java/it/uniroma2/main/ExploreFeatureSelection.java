package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.ResultsCsvWriter;
import it.uniroma2.control.FeatureSelectionExplorer;
import it.uniroma2.control.OrderedHoldoutRunner;
import it.uniroma2.control.WekaDatasetFactory;
import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.FeatureSelectionReport;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import weka.core.Instances;

/**
 * Milestone 2, Fase 7 – Esplorazione della feature selection.
 *
 * <p>Sul training dell'Ordered Holdout 80/20 (release 1–20, le prime 12.182 righe; il test non si guarda) confronta le
 * strade di {@link FeatureSelectionExplorer}: quante colonne tiene ciascuna e quali scarta.
 *
 * <p>Input: data/SYNCOPE_dataset.csv. Argomento opzionale: numero di thread per il wrapper (default: processori − 1).
 * Output: output/SYNCOPE_fs_exploration.csv, una riga per strada.
 */
public final class ExploreFeatureSelection {

    private static final Logger LOGGER = Logger.getLogger(ExploreFeatureSelection.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";

    private ExploreFeatureSelection() {
    }

    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0])
                : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        Dataset dataset = CsvReader.readDataset(Path.of("data", PROJ_NAME + "_dataset.csv"));
        Instances data = WekaDatasetFactory.create(dataset, PROJ_NAME);
        List<Integer> releaseOfRow = WekaDatasetFactory.releaseOfRows(dataset);
        int cut = OrderedHoldoutRunner.cut(releaseOfRow, OrderedHoldoutRunner.TRAIN_SHARE);
        Instances train = new Instances(data, 0, cut);
        LOGGER.info(() -> "Training dell'80/20: " + train.numInstances() + " righe, " + (train.numAttributes() - 1)
                + " feature");

        List<FeatureSelectionReport> reports = FeatureSelectionExplorer.explore(train, threads);
        for (FeatureSelectionReport r : reports) {
            LOGGER.info(() -> String.format(Locale.ROOT, "%s: tiene %d feature in %.1f s%n  tenute: %s%n  scartate: %s",
                    r.method(), r.kept().size(), r.seconds(), r.kept(), r.dropped()));
            if (!r.scores().isEmpty()) {
                LOGGER.info(() -> "  punteggi: " + r.scores().entrySet().stream()
                        .map(e -> String.format(Locale.ROOT, "%s %.4f", e.getKey(), e.getValue()))
                        .collect(Collectors.joining(", ")));
            }
        }
        Path output = Path.of("output", PROJ_NAME + "_fs_exploration.csv");
        ResultsCsvWriter.writeFeatureSelection(reports, output);
        LOGGER.info(() -> "Scritto " + output + " con " + threads + " thread");
    }
}