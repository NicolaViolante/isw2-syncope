package it.uniroma2.control;

import it.uniroma2.entity.RunResult;
import it.uniroma2.entity.ValidationKind;

import java.util.List;

import weka.core.Instances;

/**
 * Ordered Holdout 80/20 su release intere (prof: "il primo 80% come training set e il secondo 20% come testing set").
 *
 * <p>Le righe sono in ordine di release, e dentro una release in ordine di percorso della classe: lì non c'è un ordine
 * nel tempo, perché tutte le classi sono fotografate nello stesso giorno. Il taglio cade quindi sul confine di release
 * più vicino all'80% delle righe: nessuna release è divisa tra training e test, il training precede sempre il test e
 * la release resta l'unità di tempo, come in tutto il progetto. Simula il momento in cui esce una release: si conoscono
 * solo le precedenti e si predicono tutte le classi delle successive. Su SYNCOPE: release 1–20 (1.0.0–1.2.2) per il
 * training, cioè il 79,0% delle righe, e 21–24 (1.2.3–1.2.6) per il test, il 21,0%.
 */
public final class OrderedHoldoutRunner {

    public static final double TRAIN_SHARE = 0.80;

    private OrderedHoldoutRunner() {
    }

    /** @param releaseOfRow la release di ogni riga, nell'ordine dei dati ({@link WekaDatasetFactory#releaseOfRows}) */
    public static List<RunResult> run(Instances data, List<Integer> releaseOfRow, SplitTask task) throws Exception {
        if (releaseOfRow.size() != data.numInstances()) {
            throw new IllegalArgumentException("Release per " + releaseOfRow.size() + " righe, dati con "
                    + data.numInstances());
        }
        int cut = cut(releaseOfRow, TRAIN_SHARE);
        Instances train = new Instances(data, 0, cut);
        Instances test = new Instances(data, cut, data.numInstances() - cut);
        return task.evaluate(ValidationKind.ORDERED_HOLDOUT_80_20, 1, 1, train, test);
    }

    /**
     * Numero di righe della prima parte: il confine di release più vicino alla quota indicata delle righe. A pari
     * distanza vale il confine che viene prima.
     *
     * @param releaseOfRow la release di ogni riga, in ordine non decrescente
     * @param share        quota delle righe da mettere nella prima parte (0,80 per training/test e per il validation)
     * @throws IllegalArgumentException se le righe non sono in ordine di release o c'è una sola release
     */
    public static int cut(List<Integer> releaseOfRow, double share) {
        double target = share * releaseOfRow.size();
        int best = -1;
        for (int i = 1; i < releaseOfRow.size(); i++) {
            int previous = releaseOfRow.get(i - 1);
            int current = releaseOfRow.get(i);
            if (current < previous) {
                throw new IllegalArgumentException("Righe non in ordine di release alla riga " + i);
            }
            if (current != previous && (best < 0 || Math.abs(i - target) < Math.abs(best - target))) {
                best = i;
            }
        }
        if (best < 0) {
            throw new IllegalArgumentException("Serve almeno un confine di release");
        }
        return best;
    }
}