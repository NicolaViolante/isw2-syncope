package it.uniroma2.control;

import it.uniroma2.entity.ClassifierKind;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.rules.ZeroR;
import weka.classifiers.trees.RandomForest;

/**
 * Crea i classificatori con i parametri di default di Weka (nessun tuning, uguale per tutti) e seed fisso, così ogni
 * esecuzione dà gli stessi risultati.
 */
public final class ClassifierFactory {

    /** Seed di Random Forest (è anche il default di Weka, qui reso esplicito). */
    public static final int SEED = 1;

    private ClassifierFactory() {
    }

    public static Classifier create(ClassifierKind kind) {
        return switch (kind) {
            case ZERO_R -> new ZeroR();
            case RANDOM_FOREST -> {
                RandomForest rf = new RandomForest();
                rf.setSeed(SEED);
                yield rf;
            }
            case NAIVE_BAYES -> new NaiveBayes();
            case IBK -> new IBk(1);
        };
    }
}