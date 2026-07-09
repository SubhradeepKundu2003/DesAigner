package com.tcs.contentGenerator.styleextraction;

import java.util.List;

/**
 * Prompts for reference style extraction. The vision pass deliberately asks for
 * a short plain-text description per page (long prose inside JSON is where the
 * small local model breaks — same lesson as content generation); only the final
 * text-only merge call asks for structured JSON. Style only, never geometry:
 * the "LLM never produces coordinates" rule is locked — layout stays
 * deterministic Java, informed by the extracted theme and decor.
 */
final class StyleExtractionPrompts {

    static final String SYSTEM = """
            You are a senior brand designer analyzing reference newsletter designs to \
            distill their visual style. You describe style - colors, typography mood, \
            iconography, decoration, layout mood - never measurements, coordinates, or sizes.""";

    private StyleExtractionPrompts() {
    }

    static String describePage(String label) {
        return """
                This image is %s of a reference newsletter design.

                Describe its visual style in under 200 words of plain text (no JSON, no markdown):
                - Color palette: the page background color, main text color, and up to three \
                brand/accent colors, each as an approximate hex value like #1A73E8.
                - Header/masthead treatment: is there a colored banner or band at the top? Its \
                colors (hex), whether it uses a gradient, and whether its edge is straight or \
                wavy/curved.
                - Image treatment: are photos straight-edged, rounded-corner, or oval/circular? \
                Do images or cards have drop shadows or other depth effects?
                - Typography mood (e.g. rounded and friendly, corporate serif, modern geometric).
                - Iconography / graphic style, if any icons or illustrations are visible.
                - Layout mood (e.g. dense and editorial, airy single-column, card-based).""".formatted(label);
    }

    static String merge(List<String> pageNotes) {
        StringBuilder notes = new StringBuilder();
        for (int i = 0; i < pageNotes.size(); i++) {
            notes.append(i + 1).append(". ").append(pageNotes.get(i)).append("\n\n");
        }
        return """
                Below are style notes taken from the pages of one or more reference newsletter \
                designs. Merge them into a single coherent style description for a newsletter \
                design template.

                Every color field must be one six-digit hex value like #1A73E8, chosen from or \
                harmonized with the colors observed in the notes:
                - background: the page background (light or dark, follow the references)
                - surface: a subtle tint for boxes/cards, close to the background
                - text: the main body text color (must read clearly on the background)
                - muted: a softer secondary text color
                - primary: the dominant brand color
                - secondary: a supporting brand color
                - accent: a highlight color used sparingly
                - divider: a subtle rule/line color
                - mastheadFrom and mastheadTo: the two hex colors of the top banner/masthead \
                gradient the references use (if the references show no banner, reuse the primary \
                color for both)

                mastheadEdge is "wave" if the references use curved/wavy shapes or flowing lines, \
                else "flat". photoShape is "ellipse" if images appear oval/circular, "rounded" if \
                they have rounded corners, else "square". shadows is "yes" if images or cards show \
                drop shadows or depth, else "no".

                templateName is a short lowercase name for this style (e.g. "ocean-corporate"). \
                fontFamily is the single font family name that best matches the typography mood. \
                typographyMood, iconographyStyle and layoutMood are each one short sentence. \
                summary is 2-3 sentences describing the overall brand feel.

                Respond with exactly one flat JSON object holding the field VALUES — never \
                repeat the JSON schema itself. The shape of the expected answer:
                {"templateName": "ocean-corporate", "background": "#FFFFFF", "surface": "#F2F4F7", \
                "text": "#1A1A1A", "muted": "#5F6B7A", "primary": "#1A73E8", "secondary": "#FBB034", \
                "accent": "#54B948", "divider": "#E1E4E8", "fontFamily": "Avenir Next", \
                "typographyMood": "...", "iconographyStyle": "...", "layoutMood": "...", \
                "mastheadFrom": "#1A73E8", "mastheadTo": "#0B2545", "mastheadEdge": "wave", \
                "photoShape": "rounded", "shadows": "yes", "summary": "..."}

                Style notes:

                %s""".formatted(notes.toString().strip());
    }
}
