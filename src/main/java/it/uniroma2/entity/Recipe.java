package it.uniroma2.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Una ricetta: quale feature selection e quale balancing applicare al training prima del classificatore.
 * L'ordine è sempre feature selection, poi balancing.
 */
public record Recipe(FeatureSelectionKind featureSelection, BalancingKind balancing) {

    /** Nessun filtro. */
    public static final Recipe BASELINE = new Recipe(FeatureSelectionKind.NONE, BalancingKind.NONE);

    /**
     * Le 6 ricette, dalla più semplice: nessun filtro, solo feature selection, solo balancing, entrambi;
     * a parità, l'undersampling è più semplice di SMOTE. Serve anche a risolvere i pareggi sul validation.
     */
    public static List<Recipe> all() {
        List<Recipe> recipes = new ArrayList<>();
        recipes.add(BASELINE);
        recipes.add(new Recipe(FeatureSelectionKind.CFS_BACKWARD, BalancingKind.NONE));
        recipes.add(new Recipe(FeatureSelectionKind.NONE, BalancingKind.UNDERSAMPLING));
        recipes.add(new Recipe(FeatureSelectionKind.NONE, BalancingKind.SMOTE));
        recipes.add(new Recipe(FeatureSelectionKind.CFS_BACKWARD, BalancingKind.UNDERSAMPLING));
        recipes.add(new Recipe(FeatureSelectionKind.CFS_BACKWARD, BalancingKind.SMOTE));
        return recipes;
    }

    /** Posizione nell'ordine di semplicità (0 = la più semplice). */
    public int simplicity() {
        return all().indexOf(this);
    }

    public String label() {
        return featureSelection.label() + "+" + balancing.label();
    }
}