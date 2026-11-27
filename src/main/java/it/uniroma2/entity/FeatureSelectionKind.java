package it.uniroma2.entity;

/** Le opzioni di feature selection confrontate nella Milestone 2. */
public enum FeatureSelectionKind {
    NONE("none"),
    /** CfsSubsetEval con GreedyStepwise all'indietro: parte da tutte le colonne e le toglie una alla volta. */
    CFS_BACKWARD("CFS-backward");

    private final String label;

    FeatureSelectionKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}