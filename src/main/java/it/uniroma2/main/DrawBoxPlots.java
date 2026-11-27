package it.uniroma2.main;

import it.uniroma2.boundary.BoxPlotWriter;
import it.uniroma2.boundary.ResultsCsvReader;
import it.uniroma2.control.ResultsSummarizer;
import it.uniroma2.entity.Metric;
import it.uniroma2.entity.RunResult;
import it.uniroma2.entity.ValidationKind;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Milestone 2, Fase 8 – Box plot.
 *
 * <p>Rilegge le run di {@link RunExperiment} e, per ogni metrica, disegna due figure con le 100 run della 10×10:
 * <ul>
 *   <li>SYNCOPE_&lt;metrica&gt;_scelte.png: confronto 1, le versioni scelte sul validation, il campione e ZeroR;</li>
 *   <li>SYNCOPE_&lt;metrica&gt;_ricette.png: confronti 2 e 3, le 6 ricette sull'asse x e i classificatori come serie.</li>
 * </ul>
 * L'Ordered Holdout ha un solo valore per configurazione: i suoi risultati sono nelle tabelle, non in un box plot.
 *
 * <p>Input: output/SYNCOPE_m2_runs.csv. Output: output/boxplots/.
 */
public final class DrawBoxPlots {

    private static final Logger LOGGER = Logger.getLogger(DrawBoxPlots.class.getName());
    private static final String PROJ_NAME = "SYNCOPE";
    private static final ValidationKind VALIDATION = ValidationKind.CROSS_VALIDATION_10X10;

    private DrawBoxPlots() {
    }

    public static void main(String[] args) throws Exception {
        List<RunResult> runs = ResultsCsvReader.readRuns(Path.of("output", PROJ_NAME + "_m2_runs.csv"));
        Path folder = Path.of("output", "boxplots");
        for (Metric metric : Metric.values()) {
            double upper = metric == Metric.NPOFB20 ? 100.0 : 1.0;
            String unit = metric == Metric.NPOFB20 ? metric.label() + " (%)" : metric.label();
            BoxPlotWriter.write(metric.label() + " sul test - versioni scelte sul validation (" + VALIDATION.label()
                            + ")", unit, ResultsSummarizer.valuesOfChosen(runs, VALIDATION, metric), upper,
                    folder.resolve(PROJ_NAME + "_" + metric.label() + "_scelte.png"));
            BoxPlotWriter.write(metric.label() + " sul test - ricette e classificatori (" + VALIDATION.label() + ")",
                    unit, ResultsSummarizer.valuesByRecipe(runs, VALIDATION, metric), upper,
                    folder.resolve(PROJ_NAME + "_" + metric.label() + "_ricette.png"));
        }
        LOGGER.info(() -> "Scritti " + 2 * Metric.values().length + " box plot in " + folder + " da " + runs.size()
                + " run");
    }
}