package it.uniroma2.control;

import it.uniroma2.entity.BalancingKind;
import it.uniroma2.entity.ClassifierKind;
import it.uniroma2.entity.FeatureSelectionKind;
import it.uniroma2.entity.Recipe;

import java.util.ArrayList;
import java.util.List;

import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.Classifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.supervised.instance.SpreadSubsample;

/**
 * Milestone 2, Fase 3: costruisce il classificatore di una ricetta.
 *
 * <p>I filtri sono dentro un {@link FilteredClassifier}: Weka li calcola solo sui dati passati all'addestramento, cioè
 * sul training, e il test non viene mai guardato (prof: niente filtri globali, "altrimenti si fa cheating"). L'ordine
 * è tassativo: prima la feature selection (le colonne, sui dati veri), poi il balancing (le righe).
 *
 * <ul>
 *   <li>Feature selection: CfsSubsetEval con GreedyStepwise all'indietro (parte da tutte le colonne e le toglie una
 *       alla volta), come Gianpaolo.</li>
 *   <li>Undersampling: SpreadSubsample con distribuzione 1.0, cioè 50/50, come nella slide del prof.</li>
 *   <li>SMOTE: percentuale calcolata sul training per arrivare al 50/50 (con i default raddoppierebbe solo le
 *       buggy); 5 vicini e classe minoritaria trovata da Weka.</li>
 * </ul>
 * Seed: SMOTE usa il seed 1. RandomForest e SpreadSubsample ricevono invece il seed dal {@link FilteredClassifier}, che
 * lo ricava dal proprio seed (1) e dal training: Weka sovrascrive qualunque seed impostato prima. Tutto resta
 * ripetibile; solo la ricetta senza filtri usa RandomForest da solo, con il seed 1 (gli stessi numeri della Fase 2).
 */
public final class PipelineFactory {

    private PipelineFactory() {
    }

    /**
     * @param train il training su cui il classificatore verrà addestrato: serve a calcolare la percentuale di SMOTE
     */
    public static Classifier create(ClassifierKind kind, Recipe recipe, Instances train) {
        Classifier base = ClassifierFactory.create(kind);
        List<Filter> filters = new ArrayList<>();
        if (recipe.featureSelection() == FeatureSelectionKind.CFS_BACKWARD) {
            filters.add(cfsBackward());
        }
        if (recipe.balancing() == BalancingKind.UNDERSAMPLING) {
            SpreadSubsample undersampling = new SpreadSubsample();
            undersampling.setDistributionSpread(1.0);
            filters.add(undersampling);
        } else if (recipe.balancing() == BalancingKind.SMOTE) {
            SMOTE smote = new SMOTE();
            smote.setPercentage(smotePercentage(train));
            smote.setNearestNeighbors(5);
            smote.setRandomSeed(ClassifierFactory.SEED);
            filters.add(smote);
        }
        if (filters.isEmpty()) {
            return base;
        }
        FilteredClassifier filtered = new FilteredClassifier();
        if (filters.size() == 1) {
            filtered.setFilter(filters.get(0));
        } else {
            MultiFilter chain = new MultiFilter();
            chain.setFilters(filters.toArray(new Filter[0]));
            filtered.setFilter(chain);
        }
        filtered.setClassifier(base);
        return filtered;
    }

    /**
     * La percentuale di righe sintetiche che porta la classe minoritaria a pareggiare la maggioritaria. SMOTE tronca il
     * numero di righe da creare: mezza riga in più nella percentuale evita che un errore di arrotondamento ne tolga
     * una (con 1.516 buggy e 10.828 no, senza margine ne creerebbe 9.311 invece di 9.312).
     */
    public static double smotePercentage(Instances train) {
        int[] counts = train.attributeStats(train.classIndex()).nominalCounts;
        int minority = Math.min(counts[0], counts[1]);
        int majority = Math.max(counts[0], counts[1]);
        return minority == 0 ? 0.0 : 100.0 * (majority - minority + 0.5) / minority;
    }

    private static AttributeSelection cfsBackward() {
        GreedyStepwise backward = new GreedyStepwise();
        backward.setSearchBackwards(true);
        AttributeSelection selection = new AttributeSelection();
        selection.setEvaluator(new CfsSubsetEval());
        selection.setSearch(backward);
        return selection;
    }
}