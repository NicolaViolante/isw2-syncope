package it.uniroma2.control;

import it.uniroma2.entity.EvaluationOutcome;
import it.uniroma2.entity.ModelMetrics;
import it.uniroma2.entity.Prediction;

import java.util.ArrayList;
import java.util.List;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.supervised.attribute.AttributeSelection;

/**
 * Addestra un classificatore sul training e lo misura sul test. Tutte le metriche, NPofB20 compresa, vengono dalle
 * stesse predizioni dello stesso run (Valentina calcolava NPofB20 su run separate).
 */
public final class Evaluator {

    private Evaluator() {
    }

    /**
     * @param template classificatore (con i suoi filtri) da copiare: il template non viene addestrato
     */
    public static EvaluationOutcome evaluate(Classifier template, Instances train, Instances test) throws Exception {
        Classifier model = AbstractClassifier.makeCopy(template);
        model.buildClassifier(train);

        int buggy = test.classAttribute().indexOfValue(WekaDatasetFactory.BUGGY);
        int loc = test.attribute(WekaDatasetFactory.LOC_ATTRIBUTE).index();
        Evaluation evaluation = new Evaluation(train);
        List<Prediction> predictions = new ArrayList<>(test.numInstances());
        for (Instance instance : test) {
            double[] distribution = model.distributionForInstance(instance);
            evaluation.evaluateModelOnceAndRecordPrediction(distribution, instance);
            long originalLoc = Math.round(LogTransformer.invert(instance.value(loc)));
            predictions.add(new Prediction(distribution[buggy], originalLoc, (int) instance.classValue() == buggy));
        }
        ModelMetrics metrics = new ModelMetrics(evaluation.pctCorrect() / 100.0, evaluation.precision(buggy),
                evaluation.recall(buggy), evaluation.fMeasure(buggy), evaluation.areaUnderROC(buggy), evaluation.kappa(),
                NPofBCalculator.npofb20(predictions),
                (int) evaluation.numTruePositives(buggy), (int) evaluation.numFalsePositives(buggy),
                (int) evaluation.numTrueNegatives(buggy), (int) evaluation.numFalseNegatives(buggy));
        return new EvaluationOutcome(metrics, usedFeatures(model));
    }

    /**
     * Le colonne che il classificatore vede dopo i filtri, se tra i filtri c'è la feature selection (che toglie
     * colonne); altrimenti una lista vuota, cioè tutte.
     */
    private static List<String> usedFeatures(Classifier model) {
        if (!(model instanceof FilteredClassifier filtered)) {
            return List.of();
        }
        if (!hasFeatureSelection(filtered.getFilter())) {
            return List.of();
        }
        Instances format = filtered.getFilter().getOutputFormat();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < format.numAttributes(); i++) {
            Attribute attribute = format.attribute(i);
            if (i != format.classIndex()) {
                names.add(attribute.name());
            }
        }
        return names;
    }

    private static boolean hasFeatureSelection(Filter filter) {
        if (filter instanceof AttributeSelection) {
            return true;
        }
        if (filter instanceof MultiFilter chain) {
            for (Filter f : chain.getFilters()) {
                if (f instanceof AttributeSelection) {
                    return true;
                }
            }
        }
        return false;
    }
}