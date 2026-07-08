package com.tcs.contentGenerator.styleextraction;

/**
 * The LLM's merged style description (structured-output DTO for the final
 * text-only merge call — a flat object of scalar fields, which the small local
 * model handles reliably). Color fields are free-text hex candidates; nothing
 * downstream trusts them raw — {@link StyleExtractionService} validates each
 * and falls back to the default template's value per role.
 */
public record StyleDescription(
        String templateName,
        String background,
        String surface,
        String text,
        String muted,
        String primary,
        String secondary,
        String accent,
        String divider,
        String fontFamily,
        String typographyMood,
        String iconographyStyle,
        String layoutMood,
        String summary) {
}
