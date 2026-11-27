package it.uniroma2.entity;

/** Le metriche di accuratezza riportate per ogni run, sempre riferite alla classe buggy ("yes"). */
public enum Metric {
    ACCURACY("Accuracy"),
    PRECISION("Precision"),
    RECALL("Recall"),
    F1("F1"),
    AUC("AUC"),
    KAPPA("Kappa"),
    NPOFB20("NPofB20");

    private final String label;

    Metric(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}