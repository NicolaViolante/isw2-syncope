package it.uniroma2.entity;

/**
 * Uno smell segnalato da SonarCloud su un file: serve per la diagnostica da dare all'LLM (Milestone 4).
 *
 * @param line   riga dello smell (0 se riguarda il file intero)
 * @param effort tempo stimato da SonarCloud per correggerlo (es. "5min")
 */
public record SmellIssue(String rule, String severity, int line, String message, String effort) {
}
