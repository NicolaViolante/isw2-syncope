package it.uniroma2.control;

/**
 * Trasformazione logaritmica in base 10 degli attributi, come suggerito dal prof ("applicare il logaritmo in base 10
 * a tutti gli input numerici": smussa le differenze di grandezza tra le colonne e "male che vada non fa danni").
 *
 * <p>Si usa log10(1 + x) perché molte metriche valgono 0. Churn e MaxChurn possono essere negativi: per loro il segno
 * resta davanti, −log10(1 + |x|), così l'ordine dei valori non cambia. Ogni valore si trasforma da solo, senza
 * parametri stimati sui dati: applicarla prima della divisione in training e test non fa "sbirciare" il test.
 *
 * <p>Si usa {@link StrictMath} e non {@link Math}: Math può sfruttare istruzioni del processore che danno risultati
 * diversi nell'ultima cifra binaria su macchine diverse (ARM contro x86), e Random Forest, che sceglie le soglie
 * confrontando i valori, darebbe alberi diversi. StrictMath dà lo stesso risultato bit per bit ovunque.
 */
public final class LogTransformer {

    private LogTransformer() {
    }

    public static double apply(double value) {
        return Math.signum(value) * StrictMath.log10(1 + Math.abs(value));
    }

    /** L'inversa di {@link #apply}: serve a recuperare le LOC originali per NPofB20. */
    public static double invert(double transformed) {
        return Math.signum(transformed) * (StrictMath.pow(10, Math.abs(transformed)) - 1);
    }
}