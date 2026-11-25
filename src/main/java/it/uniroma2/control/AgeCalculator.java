package it.uniroma2.control;

import it.uniroma2.entity.AgeMetrics;
import it.uniroma2.entity.Touch;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Calcola Age e WeightedAge di una classe alla data di rilascio di Jira.
 *
 * <ul>
 *   <li>Age: settimane intere dal commit più vecchio che ha toccato la classe (la sua creazione);</li>
 *   <li>WeightedAge: Σ(età del commit × righe toccate) / Σ righe toccate, con l'età in settimane.</li>
 * </ul>
 * Le età negative (commit con data successiva al rilascio) valgono 0.
 */
public final class AgeCalculator {

    private static final double DAYS_PER_WEEK = 7.0;

    private AgeCalculator() {
    }

    /**
     * @param history     tutti i commit contenuti nel tag che hanno toccato la classe
     * @param releaseDate data di rilascio della release su Jira
     */
    public static AgeMetrics compute(List<Touch> history, LocalDate releaseDate) {
        if (history.isEmpty()) {
            return new AgeMetrics(0, 0.0);
        }
        LocalDate creation = history.stream().map(t -> t.commit().date()).min(LocalDate::compareTo).orElseThrow();
        long age = daysBefore(creation, releaseDate) / 7;

        double weighted = 0.0;
        long touched = 0;
        for (Touch t : history) {
            weighted += daysBefore(t.commit().date(), releaseDate) / DAYS_PER_WEEK * t.change().touched();
            touched += t.change().touched();
        }
        double weightedAge = touched == 0 ? age : weighted / touched;
        return new AgeMetrics(age, Math.round(weightedAge * 100.0) / 100.0);
    }

    /**
     * Età di una classe che nessun commit (merge esclusi) ha toccato: è nata in un commit di merge.
     * Age parte dal giorno di quel commit; WeightedAge coincide con Age.
     */
    public static AgeMetrics fromCreation(LocalDate creation, LocalDate releaseDate) {
        long age = daysBefore(creation, releaseDate) / 7;
        return new AgeMetrics(age, age);
    }

    private static long daysBefore(LocalDate day, LocalDate releaseDate) {
        return Math.max(0, ChronoUnit.DAYS.between(day, releaseDate));
    }
}