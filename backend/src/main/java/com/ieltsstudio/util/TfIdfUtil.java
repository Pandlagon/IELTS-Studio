package com.ieltsstudio.util;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TF-IDF utility for locating answer sentences in passages.
 * Used to highlight the relevant portion of a reading passage for a given question.
 */
@Component
public class TfIdfUtil {

    public String findLocatorSentence(String passage, String questionText) {
        if (passage == null || questionText == null) return "";

        String[] sentences = passage.split("[.!?]+\\s+");
        if (sentences.length == 0) return "";

        Map<String, Double> questionTf = computeTf(tokenize(questionText));
        double bestScore = -1;
        String bestSentence = "";

        for (String sentence : sentences) {
            if (sentence.trim().length() < 20) continue;
            Map<String, Double> sentenceTf = computeTf(tokenize(sentence));
            double score = cosineSimilarity(questionTf, sentenceTf);
            if (score > bestScore) {
                bestScore = score;
                bestSentence = sentence.trim();
            }
        }

        return bestScore > 0.1 ? bestSentence : "";
    }

    public List<String> findTopSentences(String passage, String questionText, int topN) {
        if (passage == null || questionText == null) return Collections.emptyList();

        String[] sentences = passage.split("[.!?]+\\s+");
        Map<String, Double> questionTf = computeTf(tokenize(questionText));

        List<Map.Entry<String, Double>> scored = new ArrayList<>();
        for (String sentence : sentences) {
            if (sentence.trim().length() < 15) continue;
            Map<String, Double> sentenceTf = computeTf(tokenize(sentence));
            double score = cosineSimilarity(questionTf, sentenceTf);
            scored.add(Map.entry(sentence.trim(), score));
        }

        return scored.stream()
                .filter(e -> e.getValue() > 0.05)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .split("\\s+"))
                .filter(t -> t.length() > 2 && !STOP_WORDS.contains(t))
                .collect(Collectors.toList());
    }

    private Map<String, Double> computeTf(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        if (tokens.isEmpty()) return tf;
        for (String token : tokens) {
            tf.merge(token, 1.0, Double::sum);
        }
        tf.replaceAll((k, v) -> v / tokens.size());
        return tf;
    }

    private double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        double dot = 0, norm1 = 0, norm2 = 0;
        for (Map.Entry<String, Double> e : v1.entrySet()) {
            double val2 = v2.getOrDefault(e.getKey(), 0.0);
            dot += e.getValue() * val2;
            norm1 += e.getValue() * e.getValue();
        }
        for (double v : v2.values()) {
            norm2 += v * v;
        }
        if (norm1 == 0 || norm2 == 0) return 0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
            "has", "have", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "that", "this", "these", "those",
            "it", "its", "he", "she", "they", "we", "you", "i", "not", "no"
    ));
}
