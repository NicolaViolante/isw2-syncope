package it.uniroma2.control;

import it.uniroma2.entity.RunResult;
import it.uniroma2.entity.ValidationKind;

import java.util.List;

import weka.core.Instances;

/** Il lavoro da fare su una coppia training/test prodotta da una validazione. */
@FunctionalInterface
public interface SplitTask {

    List<RunResult> evaluate(ValidationKind validation, int repetition, int fold, Instances train, Instances test)
            throws Exception;
}