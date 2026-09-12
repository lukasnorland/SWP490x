package com.funix.swp490x.mrs.llm;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Gemini text models P-06d may pick for FT-04 interpretation.
 *
 * <p>Listed from {@code models.list} against the configured API key. Image,
 * TTS, research, computer-use and Gemma ids are omitted: Search needs
 * structured JSON, not those modalities.
 */
public final class GeminiModels {

    public record Option(String id, String label) {
    }

    public static final List<Option> OPTIONS = List.of(
            new Option("gemini-3.8-flash", "Gemini 3.8 Flash"),
            new Option("gemini-3.7-flash", "Gemini 3.7 Flash"),
            new Option("gemini-3.6-flash", "Gemini 3.6 Flash"),
            new Option("gemini-3.5-flash", "Gemini 3.5 Flash"),
            new Option("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite"),
            new Option("gemini-3-flash-preview", "Gemini 3 Flash Preview"),
            new Option("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite"),
            new Option("gemini-2.5-flash", "Gemini 2.5 Flash"),
            new Option("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite"),
            new Option("gemini-2.5-pro", "Gemini 2.5 Pro"));

    private GeminiModels() {
    }

    public static boolean isKnown(String id) {
        if (!StringUtils.hasText(id)) {
            return false;
        }
        for (Option option : OPTIONS) {
            if (option.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Dropdown rows, with a saved-but-unlisted id kept so the current value still displays. */
    public static List<Option> forForm(String current) {
        if (!StringUtils.hasText(current) || isKnown(current)) {
            return OPTIONS;
        }
        List<Option> options = new ArrayList<>();
        options.add(new Option(current, current + " (saved)"));
        options.addAll(OPTIONS);
        return List.copyOf(options);
    }
}
