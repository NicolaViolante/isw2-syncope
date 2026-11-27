package it.uniroma2.entity;

/** I classificatori della Milestone 2: i tre richiesti dalla specifica più la baseline ZeroR. */
public enum ClassifierKind {
    /** Predice sempre la classe più frequente: la baseline che ogni classificatore deve battere. */
    ZERO_R("ZeroR"),
    RANDOM_FOREST("RandomForest"),
    NAIVE_BAYES("NaiveBayes"),
    /** k-nearest neighbours con k = 1 (default di Weka). */
    IBK("IBk");

    private final String label;

    ClassifierKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** I tre classificatori in gara (senza la baseline). */
    public static ClassifierKind[] competitors() {
        return new ClassifierKind[] {RANDOM_FOREST, NAIVE_BAYES, IBK};
    }
}