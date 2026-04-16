package com.newplanner.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Fallback-Waterfall API Key Manager.
 *
 * Strategy: Always try key[0] first. If it fails (rate limit / HTTP error / exception),
 * automatically waterfall to key[1], key[2], ... until one succeeds.
 *
 * This is ideal for free-tier accounts: key[0] absorbs load until rate-limited,
 * then the next fresh key takes over seamlessly.
 *
 * Also maintains a round-robin cursor so repeated calls cycle starting points,
 * distributing load evenly when all keys are healthy.
 */
@Service
public class ApiKeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyRotationService.class);

    // ── Raw comma-separated key lists from application.properties ─────────────

    @Value("${api.opentripmap.keys:}")
    private String otmRaw;

    @Value("${api.ors.keys:}")
    private String orsRaw;

    @Value("${api.openweather.keys:}")
    private String weatherRaw;

    @Value("${api.groq.keys:}")
    private String groqRaw;

    @Value("${api.gemini.key:}")
    private String geminiKey;

    @Value("${api.openrouter.key:}")
    private String openRouterKey;

    // ── Parsed key lists ──────────────────────────────────────────────────────

    private List<String> otmKeys;
    private List<String> orsKeys;
    private List<String> weatherKeys;
    private List<String> groqKeys;

    // ── Round-robin start cursors (even when all healthy, spread across keys) ─

    private final AtomicInteger otmCursor     = new AtomicInteger(0);
    private final AtomicInteger orsCursor     = new AtomicInteger(0);
    private final AtomicInteger weatherCursor = new AtomicInteger(0);
    private final AtomicInteger groqCursor    = new AtomicInteger(0);

    @PostConstruct
    private void init() {
        otmKeys     = parseKeys(otmRaw);
        orsKeys     = parseKeys(orsRaw);
        weatherKeys = parseKeys(weatherRaw);
        groqKeys    = parseKeys(groqRaw);

        log.info("[KeyRotation] Loaded — OTM: {} key(s) | ORS: {} key(s) | Weather: {} key(s) | Groq: {} key(s)",
                otmKeys.size(), orsKeys.size(), weatherKeys.size(), groqKeys.size());
    }

    // ── Public key-list accessors (used by each service for fallback loops) ───

    public List<String> getOtmKeys()     { return rotatedView(otmKeys,     otmCursor);     }
    public List<String> getOrsKeys()     { return rotatedView(orsKeys,     orsCursor);     }
    public List<String> getWeatherKeys() { return rotatedView(weatherKeys, weatherCursor); }
    public List<String> getGroqKeys()    { return rotatedView(groqKeys,    groqCursor);    }
    public String       getGeminiKey()   { return geminiKey;    }
    public String       getOpenRouterKey() { return openRouterKey; }

    // ── Generic fallback executor ─────────────────────────────────────────────

    /**
     * Tries each key in order. Returns on first success.
     * If ALL keys fail, throws the last caught exception.
     *
     * Usage:
     *   String result = keyService.tryWithFallback(keyService.getOtmKeys(), key -> {
     *       // use key here, throw on failure
     *   });
     */
    @FunctionalInterface
    public interface KeyAction<T> {
        T execute(String key) throws Exception;
    }

    public <T> T tryWithFallback(List<String> keys, KeyAction<T> action) {
        if (keys.isEmpty()) throw new RuntimeException("No API keys configured.");
        Exception last = null;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            try {
                T result = action.execute(key);
                return result;
            } catch (Exception e) {
                last = e;
                String shortKey = key.length() > 8 ? key.substring(0, 8) + "..." : key;
                log.warn("[KeyRotation] Key [{}] ({}/{}) failed: {} — trying next key.",
                        shortKey, i + 1, keys.size(), e.getMessage());
            }
        }
        throw new RuntimeException("All " + keys.size() + " API keys exhausted. Last error: " + last.getMessage(), last);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Returns a rotated view of the key list starting from the current cursor.
     * Advances the cursor so next call starts from the next key (round-robin start).
     */
    private List<String> rotatedView(List<String> keys, AtomicInteger cursor) {
        if (keys.isEmpty()) return keys;
        int start = Math.abs(cursor.getAndIncrement() % keys.size());
        // Rotate: [start..end] + [0..start-1]
        List<String> rotated = new java.util.ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            rotated.add(keys.get((start + i) % keys.size()));
        }
        return rotated;
    }

    private List<String> parseKeys(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .collect(Collectors.toList());
    }
}
