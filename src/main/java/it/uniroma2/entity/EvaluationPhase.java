package it.uniroma2.entity;

/**
 * Dove viene misurato un modello (slide "Tuning" del prof): sul validation si sceglie, sul test si valuta "a giochi
 * fatti". I valori del validation servono solo alle scelte e sono ottimisti per costruzione.
 */
public enum EvaluationPhase {
    /** Addestrato sul training interno, misurato sul validation: serve a scegliere ricetta e campione. */
    VALIDATION("validation"),
    /** Addestrato su tutto il training (training interno + validation), misurato sul test. */
    TEST("test");

    private final String label;

    EvaluationPhase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}