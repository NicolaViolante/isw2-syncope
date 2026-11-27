package it.uniroma2.entity;

/** Le opzioni di balancing confrontate nella Milestone 2; l'ordine è dalla più semplice. */
public enum BalancingKind {
    NONE("none"),
    /** SpreadSubsample -M 1.0: toglie righe "no" fino al 50/50. */
    UNDERSAMPLING("undersampling"),
    /** SMOTE con la percentuale che porta al 50/50: crea righe "yes" sintetiche. */
    SMOTE("SMOTE");

    private final String label;

    BalancingKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}