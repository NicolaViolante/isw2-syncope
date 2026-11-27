package it.uniroma2.entity;

/**
 * Le versioni di un ticket sulla linea principale (Release ID della Fase 1).
 *
 * @param ov     Opening Version; null se il ticket è stato aperto prima della prima release
 * @param fv     Fix Version: prima release dopo l'OV il cui tag contiene un fix; null se non esiste
 * @param iv     Injected Version; null per i ticket esclusi
 * @param source da dove viene la IV, oppure perché il ticket è escluso
 */
public record TicketVersions(String key, Integer ov, Integer fv, Integer iv, Source source) {

    /** Origine della IV o motivo di esclusione. */
    public enum Source {
        /** prima AV, coerente (≤ OV) */
        AV,
        /** Proportion: il ticket non ha AV utilizzabili */
        PROPORTION_NO_AV,
        /** Proportion: la prima AV è successiva all'OV */
        PROPORTION_INCOHERENT_AV,
        /** escluso: aperto prima della prima release (nessuna OV) */
        EXCLUDED_NO_OV,
        /** escluso: nessuna release dopo l'OV contiene un commit di fix (senza commit, fix solo su rami scartati o
         *  non ancora rilasciato) */
        EXCLUDED_NO_FV
    }

    public boolean isExcluded() {
        return source == Source.EXCLUDED_NO_OV || source == Source.EXCLUDED_NO_FV;
    }
}