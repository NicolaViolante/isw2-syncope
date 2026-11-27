package it.uniroma2.control;

import it.uniroma2.entity.Dataset;
import it.uniroma2.entity.DatasetRow;

import java.util.ArrayList;
import java.util.List;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

/**
 * Milestone 2, Fase 1: dal dataset della Milestone 1 ai dati di Weka.
 *
 * <ul>
 *   <li>Attributi: le 20 feature e NSmells, tutti numerici e trasformati con {@link LogTransformer}.</li>
 *   <li>Tolte Project e Class (testo; il nome della classe farebbe imparare a memoria la classe) e ReleaseID (dice
 *       "quanto era buggy quella release", un'informazione sull'etichetta e non sul codice).</li>
 *   <li>La classe da predire, Bugginess {no, yes}, è l'ultimo attributo.</li>
 *   <li>Le righe restano nell'ordine del dataset, cioè per release: serve all'Ordered Holdout.</li>
 * </ul>
 */
public final class WekaDatasetFactory {

    public static final String CLASS_ATTRIBUTE = "Bugginess";
    public static final String BUGGY = "yes";
    public static final String LOC_ATTRIBUTE = "LOC";
    private static final String SMELLS_ATTRIBUTE = "NSmells";

    private WekaDatasetFactory() {
    }

    /** La release di ogni riga, nello stesso ordine delle righe di {@link #create}: serve all'Ordered Holdout. */
    public static List<Integer> releaseOfRows(Dataset dataset) {
        return dataset.rows().stream().map(DatasetRow::releaseId).toList();
    }

    public static Instances create(Dataset dataset, String relationName) {
        ArrayList<Attribute> attributes = new ArrayList<>();
        for (String feature : dataset.featureNames()) {
            attributes.add(new Attribute(feature));
        }
        attributes.add(new Attribute(SMELLS_ATTRIBUTE));
        attributes.add(new Attribute(CLASS_ATTRIBUTE, List.of("no", BUGGY)));

        Instances data = new Instances(relationName, attributes, dataset.rows().size());
        data.setClassIndex(attributes.size() - 1);
        int features = dataset.featureNames().size();
        for (DatasetRow row : dataset.rows()) {
            double[] values = new double[attributes.size()];
            for (int i = 0; i < features; i++) {
                values[i] = LogTransformer.apply(Double.parseDouble(row.features().get(i)));
            }
            values[features] = LogTransformer.apply(row.nSmells());
            values[features + 1] = row.buggy() ? 1 : 0;
            data.add(new DenseInstance(1.0, values));
        }
        return data;
    }
}