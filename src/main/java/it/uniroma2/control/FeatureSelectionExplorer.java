package it.uniroma2.control;

import it.uniroma2.entity.ClassifierKind;
import it.uniroma2.entity.FeatureSelectionReport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.ASSearch;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.attributeSelection.WrapperSubsetEval;
import weka.core.Instances;
import weka.core.SelectedTag;

/**
 * Milestone 2, Fase 7: confronta più strade di feature selection sullo stesso training, per vedere se quella usata
 * nell'esperimento (CfsSubsetEval all'indietro) è troppo aggressiva o scarta feature che le altre considerano utili.
 *
 * <ul>
 *   <li>CfsSubsetEval con GreedyStepwise all'indietro: la strada dell'esperimento (come Gianpaolo).</li>
 *   <li>CfsSubsetEval con BestFirst in avanti: il default di Weka, stessa misura con un'altra ricerca.</li>
 *   <li>InfoGainAttributeEval con Ranker: un punteggio per ogni feature; si riportano le prime 6, come Valentina.</li>
 *   <li>WrapperSubsetEval con GreedyStepwise all'indietro, per Naive Bayes e IBk: sceglie le colonne con il
 *       classificatore stesso (5 fold interni, seed 1). Weka non offre la Kappa come misura: si usa l'AUC della classe
 *       buggy, il secondo criterio della scelta sul validation. Per Random Forest il wrapper richiederebbe centinaia di
 *       addestramenti di 100 alberi e non si esegue.</li>
 * </ul>
 * Si guarda solo il training: il test non entra mai nelle scelte.
 */
public final class FeatureSelectionExplorer {

    public static final int INFO_GAIN_TOP = 6;
    private static final int WRAPPER_FOLDS = 5;

    private FeatureSelectionExplorer() {
    }

    /**
     * @param threads sottoinsiemi valutati in parallelo dal wrapper (Weka raccoglie i risultati in ordine: stesse
     *                scelte con qualunque numero di thread)
     */
    public static List<FeatureSelectionReport> explore(Instances train, int threads) throws Exception {
        List<FeatureSelectionReport> reports = new ArrayList<>();
        GreedyStepwise backward = new GreedyStepwise();
        backward.setSearchBackwards(true);
        reports.add(subset("CfsSubsetEval + GreedyStepwise all'indietro", new CfsSubsetEval(), backward, train));
        reports.add(subset("CfsSubsetEval + BestFirst in avanti", new CfsSubsetEval(), new BestFirst(), train));
        reports.add(infoGain(train));
        for (ClassifierKind kind : new ClassifierKind[] {ClassifierKind.NAIVE_BAYES, ClassifierKind.IBK}) {
            WrapperSubsetEval wrapper = new WrapperSubsetEval();
            wrapper.setClassifier(ClassifierFactory.create(kind));
            wrapper.setFolds(WRAPPER_FOLDS);
            wrapper.setSeed(ClassifierFactory.SEED);
            wrapper.setEvaluationMeasure(new SelectedTag(WrapperSubsetEval.EVAL_AUC, WrapperSubsetEval.TAGS_EVALUATION));
            wrapper.setIRClassValue(WekaDatasetFactory.BUGGY);
            GreedyStepwise wrapperBackward = new GreedyStepwise();
            wrapperBackward.setSearchBackwards(true);
            wrapperBackward.setNumExecutionSlots(threads);
            reports.add(subset("Wrapper " + kind.label() + " (AUC) + GreedyStepwise all'indietro", wrapper,
                    wrapperBackward, train));
        }
        return reports;
    }

    private static FeatureSelectionReport subset(String method, ASEvaluation evaluator, ASSearch search,
                                                 Instances train) throws Exception {
        long start = System.nanoTime();
        AttributeSelection selection = new AttributeSelection();
        selection.setEvaluator(evaluator);
        selection.setSearch(search);
        selection.SelectAttributes(train);
        List<String> kept = new ArrayList<>();
        for (int index : selection.selectedAttributes()) {
            if (index != train.classIndex()) {
                kept.add(train.attribute(index).name());
            }
        }
        return new FeatureSelectionReport(method, kept, dropped(train, kept), Map.of(), elapsed(start));
    }

    private static FeatureSelectionReport infoGain(Instances train) throws Exception {
        long start = System.nanoTime();
        AttributeSelection selection = new AttributeSelection();
        selection.setEvaluator(new InfoGainAttributeEval());
        selection.setSearch(new Ranker());
        selection.SelectAttributes(train);
        Map<String, Double> scores = new LinkedHashMap<>();
        for (double[] ranked : selection.rankedAttributes()) {
            scores.put(train.attribute((int) ranked[0]).name(), ranked[1]);
        }
        List<String> kept = scores.keySet().stream().limit(INFO_GAIN_TOP).toList();
        return new FeatureSelectionReport("InfoGainAttributeEval + Ranker (prime " + INFO_GAIN_TOP + ")", kept,
                dropped(train, kept), scores, elapsed(start));
    }

    private static List<String> dropped(Instances train, List<String> kept) {
        List<String> dropped = new ArrayList<>();
        for (int i = 0; i < train.numAttributes(); i++) {
            if (i != train.classIndex() && !kept.contains(train.attribute(i).name())) {
                dropped.add(train.attribute(i).name());
            }
        }
        return dropped;
    }

    private static double elapsed(long start) {
        return (System.nanoTime() - start) / 1e9;
    }
}