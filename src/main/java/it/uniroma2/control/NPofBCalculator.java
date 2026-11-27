package it.uniroma2.control;

import it.uniroma2.entity.Prediction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * NPofB20 come spiegato a lezione: chi deve controllare il codice segue la classifica delle classi ordinate per
 * probabilità predetta di bug <b>divisa per le LOC</b> (la normalizzazione della "N": a parità di probabilità conviene
 * aprire la classe più piccola), e ispeziona classi finché non ha letto il 20% delle LOC del test. NPofB20 è la
 * percentuale delle righe buggy del test trovate entro quel budget.
 *
 * <p>Ci si ferma alla prima classe che farebbe superare il budget, come Valentina. A parità di punteggio vale l'ordine
 * del test (ordinamento stabile).
 */
public final class NPofBCalculator {

    private static final double BUDGET = 0.20;

    private NPofBCalculator() {
    }

    /** @return NPofB20 in percentuale (0–100); 0 se il test non ha righe buggy */
    public static double npofb20(List<Prediction> predictions) {
        long totalLoc = 0;
        long totalBuggy = 0;
        for (Prediction p : predictions) {
            totalLoc += p.loc();
            if (p.buggy()) {
                totalBuggy++;
            }
        }
        if (totalBuggy == 0) {
            return 0.0;
        }
        List<Prediction> ranking = new ArrayList<>(predictions);
        ranking.sort(Comparator.comparingDouble((Prediction p) -> p.probabilityBuggy() / p.loc()).reversed());
        double budget = BUDGET * totalLoc;
        long inspected = 0;
        long found = 0;
        for (Prediction p : ranking) {
            if (inspected + p.loc() > budget) {
                break;
            }
            inspected += p.loc();
            if (p.buggy()) {
                found++;
            }
        }
        return 100.0 * found / totalBuggy;
    }
}