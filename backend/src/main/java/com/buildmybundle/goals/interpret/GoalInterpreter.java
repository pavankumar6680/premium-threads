package com.buildmybundle.goals.interpret;

/** Turns a natural-language shopping goal into structured, reviewable requirements. */
public interface GoalInterpreter {

	GoalAnalysis interpret(String text);
}
