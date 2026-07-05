package com.tcs.contentGenerator.design;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * The design model's JSON *is* the editor's API payload (see TASKS.md's
 * "Design Model architecture"), so it must round-trip through Jackson exactly:
 * serialize, deserialize, and the result must equal the original — for one of
 * each {@link Component} shape.
 */
class DesignDocumentSerializationTest {

    @Test
    void roundTripsThroughJson() {
        Theme theme = new Theme(
                new PageSize(595.28, 841.89),
                Map.of("background", "#FFFFFF", "primary", "#0B5FFF"),
                Map.of("Body", new TextStyle("SansSerif", 10, "normal", "text", 14)),
                new Spacing(48, 16));

        SourceLink source = new SourceLink("Delivery Highlights", "NPS climbs to 72");
        TextBox text = new TextBox("cmp-1", ComponentRole.ARTICLE_HEADLINE,
                new Frame(48, 48, 300, 20), 0, false, source, "Headline", "NPS climbs to 72");
        ImageBox image = new ImageBox("cmp-2", ComponentRole.IMAGE_PLACEHOLDER,
                new Frame(48, 80, 200, 120), 1, false, null, null, "Team photo");
        ShapeBox shape = new ShapeBox("cmp-3", ComponentRole.DIVIDER,
                new Frame(48, 210, 500, 2), 2, true, null, "rect", "divider");
        Page page = new Page("page-1", List.of(text, image, shape));

        DesignDocument original = new DesignDocument(1, 1,
                new DesignMeta("TD Monthly — July 2026", "job-123"), theme, List.of(), List.of(page));

        JsonMapper mapper = new JsonMapper();
        String json = mapper.writeValueAsString(original);
        DesignDocument roundTripped = mapper.readValue(json, DesignDocument.class);

        assertEquals(original, roundTripped);
    }
}
