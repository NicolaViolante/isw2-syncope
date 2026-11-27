package it.uniroma2.entity;

/** Le due tecniche di validazione della Milestone 2 (la "mossa da 30" del prof). */
public enum ValidationKind {
    /** 10 times 10-fold cross validation stratificata: la validazione principale della specifica. */
    CROSS_VALIDATION_10X10("10x10-CV"),
    /** Ordered Holdout: primo 80% delle righe (in ordine di release) per il training, ultimo 20% per il test. */
    ORDERED_HOLDOUT_80_20("OrderedHoldout-80/20");

    private final String label;

    ValidationKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}