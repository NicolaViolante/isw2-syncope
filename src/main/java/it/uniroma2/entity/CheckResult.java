package it.uniroma2.entity;

/**
 * L'esito di un controllo sul dataset (Fase 10).
 *
 * @param description cosa si controlla
 * @param count       righe (o casi) che violano la regola; per i controlli informativi, i casi trovati
 * @param example     il primo caso trovato, vuoto se nessuno
 * @param blocking    true se una sola violazione indica un errore; false per i dati da dichiarare nel report
 */
public record CheckResult(String description, long count, String example, boolean blocking) {

    public boolean passed() {
        return !blocking || count == 0;
    }
}