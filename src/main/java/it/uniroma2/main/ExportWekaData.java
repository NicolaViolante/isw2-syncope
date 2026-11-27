package it.uniroma2.main;

import it.uniroma2.boundary.CsvReader;
import it.uniroma2.boundary.WekaDataWriter;
import it.uniroma2.control.ExperimentTask;
import it.uniroma2.control.OrderedHoldoutRunner;
import it.uniroma2.control.WekaDatasetFactory;
import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.Release;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import weka.core.Instances;

/**
 * Milestone 2, Fase 1 – Dati per Weka.
 *
 * <p>Converte il dataset della Milestone 1 nel formato di Weka: 21 attributi (20 feature e NSmells) trasformati con
 * log10, senza Project, Class e ReleaseID, con Bugginess come ultimo attributo e le righe in ordine di release.
 *
 * <p>Mostra anche come l'Ordered Holdout divide le righe: training interno, validation e test su release intere.
 *
 * <p>Input: data/SYNCOPE_dataset.csv e data/SYNCOPE_releases.csv. Output: output/SYNCOPE_weka.arff (si può aprire
 * anche nell'Explorer di Weka).
 */
public final class ExportWekaData {

    private static final Logger LOGGER = Logger.getLogger(ExportWekaData.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";

    private ExportWekaData() {
    }

    public static void main(String[] args) throws IOException {
        Dataset dataset = CsvReader.readDataset(Path.of("data", PROJ_NAME + "_dataset.csv"));
        Instances data = WekaDatasetFactory.create(dataset, PROJ_NAME);
        List<Integer> releaseOfRow = WekaDatasetFactory.releaseOfRows(dataset);
        Map<Integer, String> names = new HashMap<>();
        for (Release r : CsvReader.readMainLine(Path.of("data", PROJ_NAME + "_releases.csv"), true)) {
            names.put(r.seqId(), r.name());
        }
        Path output = Path.of("output", PROJ_NAME + "_weka.arff");
        WekaDataWriter.writeArff(data, output);

        int buggy = buggy(data, 0, data.numInstances());
        StringBuilder attributes = new StringBuilder();
        for (int i = 0; i < data.numAttributes(); i++) {
            attributes.append(i == 0 ? "" : ", ").append(data.attribute(i).name());
        }
        LOGGER.info(() -> String.format(Locale.ROOT, "Righe: %d | attributi: %d (%s)", data.numInstances(),
                data.numAttributes(), attributes));
        LOGGER.info(() -> String.format(Locale.ROOT, "Buggy: %d (%.2f%%) | ZeroR avrebbe accuracy %.2f%%", buggy,
                100.0 * buggy / data.numInstances(), 100.0 * (data.numInstances() - buggy) / data.numInstances()));

        int cut = OrderedHoldoutRunner.cut(releaseOfRow, OrderedHoldoutRunner.TRAIN_SHARE);
        int inner = OrderedHoldoutRunner.cut(releaseOfRow.subList(0, cut), 1 - ExperimentTask.VALIDATION_SHARE);
        LOGGER.info(() -> "Ordered Holdout 80/20 su release intere");
        LOGGER.info(() -> part("training", data, releaseOfRow, names, 0, cut) + " = "
                + part("training interno", data, releaseOfRow, names, 0, inner) + " + "
                + part("validation", data, releaseOfRow, names, inner, cut));
        LOGGER.info(() -> part("test", data, releaseOfRow, names, cut, data.numInstances()));
        LOGGER.info(() -> String.format(Locale.ROOT, "  quote: training %.1f%% / test %.1f%% delle righe; dentro il "
                        + "training, training interno %.1f%% / validation %.1f%%", 100.0 * cut / data.numInstances(),
                100.0 * (data.numInstances() - cut) / data.numInstances(), 100.0 * inner / cut,
                100.0 * (cut - inner) / cut));
        LOGGER.info(() -> "Scritto " + output);
    }

    /** Una parte dell'Ordered Holdout: release, righe e buggy. */
    private static String part(String label, Instances data, List<Integer> releaseOfRow, Map<Integer, String> names,
                               int from, int to) {
        int rows = to - from;
        int buggy = buggy(data, from, to);
        return String.format(Locale.ROOT, "%s release %d-%d (%s - %s): %d righe, %d buggy (%.2f%%)", label,
                releaseOfRow.get(from), releaseOfRow.get(to - 1), names.get(releaseOfRow.get(from)),
                names.get(releaseOfRow.get(to - 1)), rows, buggy, 100.0 * buggy / rows);
    }

    private static int buggy(Instances data, int from, int to) {
        int buggy = 0;
        for (int i = from; i < to; i++) {
            if (data.instance(i).stringValue(data.classIndex()).equals(WekaDatasetFactory.BUGGY)) {
                buggy++;
            }
        }
        return buggy;
    }
}