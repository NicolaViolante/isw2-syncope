package it.uniroma2.entity;

/**
 * Le metriche di un modello su un test set, per la classe buggy ("yes").
 *
 * @param npofb20 percentuale (0–100) delle righe buggy del test trovate ispezionando il 20% delle LOC, seguendo
 *                l'ordine della probabilità predetta divisa per le LOC
 */
public record ModelMetrics(double accuracy, double precision, double recall, double f1, double auc, double kappa,
                           double npofb20, int tp, int fp, int tn, int fn) {

    public double get(Metric metric) {
        return switch (metric) {
            case ACCURACY -> accuracy;
            case PRECISION -> precision;
            case RECALL -> recall;
            case F1 -> f1;
            case AUC -> auc;
            case KAPPA -> kappa;
            case NPOFB20 -> npofb20;
        };
    }
}