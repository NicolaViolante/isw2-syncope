package it.uniroma2.entity;

/**
 * La modifica a un file .java dentro un commit, rispetto al primo genitore.
 *
 * <p>Per le aggiunte oldPath è null, per le cancellazioni newPath è null.
 */
public record FileChange(Kind kind, String oldPath, String newPath, int added, int deleted) {

    /** Il tipo di modifica, come lo riconosce Git (con il rilevamento delle rinomine). */
    public enum Kind { ADD, MODIFY, DELETE, RENAME, COPY }

    /** Righe toccate: aggiunte + cancellate. */
    public int touched() {
        return added + deleted;
    }

    /** Churn della modifica: aggiunte − cancellate (può essere negativo). */
    public int churn() {
        return added - deleted;
    }
}
