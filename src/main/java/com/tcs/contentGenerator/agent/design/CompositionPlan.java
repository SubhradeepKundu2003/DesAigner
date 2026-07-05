package com.tcs.contentGenerator.agent.design;

import java.util.List;

/** The semantic layout plan for the whole issue, ready for the layout engine. */
public record CompositionPlan(String templateName, List<SectionComposition> sections) {

    public CompositionPlan {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
