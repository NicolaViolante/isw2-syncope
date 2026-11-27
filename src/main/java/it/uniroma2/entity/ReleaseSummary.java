package it.uniroma2.entity;

import java.time.LocalDate;

/**
 * I numeri di una release del dataset finale (Fase 10), per le tabelle del report.
 *
 * @param classes righe del dataset nella release (classi presenti nello snapshot)
 * @param touched classi toccate da almeno un commit della release (NR &gt; 0)
 * @param buggy   righe con Bugginess = yes
 * @param nSmells somma di NSmells sulle classi della release
 */
public record ReleaseSummary(int releaseId, String version, LocalDate date, int classes, int touched, int buggy,
                             long nSmells) {

    public double buggyPercent() {
        return classes == 0 ? 0.0 : 100.0 * buggy / classes;
    }
}