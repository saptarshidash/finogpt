package com.saptarshi.finogpt.helper;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NLPKeywordExtractor {

    private static final Set<String> STOPWORDS = Set.of(
            "show", "me", "the", "all", "of", "in", "for", "on", "at", "to",
            "my", "i", "did", "how", "much", "from", "by", "with", "and",
            "what", "which", "is", "was", "were", "had", "a", "an", "this", "that", "like",
            "made", "make", "am", "do", "who", "whom", "where", "latest", "recent", "newest",
            "between", "before", "after", "first"
    );

    private static final Set<String> INTENT_WORDS = Set.of(
            "total", "sum", "spent", "spend", "spending", "count", "average", "avg",
            "top", "highest", "lowest", "maximum", "minimum", "largest", "smallest",
            "max", "min", "most", "least", "amount", "expense", "expenses",
            "credit", "credits", "debit", "debits", "receive", "received", "receiving",
            "income", "salary", "refund", "refunds", "refunded", "credited", "earn", "earned"
    );

    private static final Set<String> TIME_WORDS = Set.of(
            "today", "yesterday", "last", "this", "month", "months", "week", "year",
            "past", "previous", "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
            "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
            "quarter", "quarters", "q1", "q2", "q3", "q4", "day", "days"
    );

    private static final Set<String> NOISE_WORDS = Set.of(
            "transaction", "transactions", "payment", "payments", "record", "records",
            "history", "merchant", "merchants", "category", "categories", "related", "similar"
    );

    private static final Set<String> ENTITY_HINTS = Set.of(
            "merchant", "merchants", "vendor", "vendors", "shop", "shops", "store", "stores",
            "who", "whom"
    );

    private static final Set<String> CATEGORY_HINTS = Set.of(
            "category", "categories", "expense", "expenses"
    );

    private static final Set<String> GENERIC_PHRASES = Set.of(
            "pay", "shop", "store", "vendor", "merchant", "service", "services"
    );

    public String normalize(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public String extractSearchPhrase(String query) {
        String normalized = normalize(query);

        List<String> tokens = Arrays.stream(normalized.split(" "))
                .filter(token -> !token.isBlank())
                .filter(token -> !token.matches("\\d+"))
                .filter(token -> !STOPWORDS.contains(token))
                .filter(token -> !INTENT_WORDS.contains(token))
                .filter(token -> !TIME_WORDS.contains(token))
                .filter(token -> !NOISE_WORDS.contains(token))
                .toList();

        return tokens.stream().collect(Collectors.joining(" ")).trim();
    }

    public boolean isEntityFocused(String query) {
        return containsHint(query, ENTITY_HINTS);
    }

    public boolean isCategoryFocused(String query) {
        return containsHint(query, CATEGORY_HINTS);
    }

    public boolean isImplicitEntityRankingQuestion(String query) {
        String normalized = normalize(query);
        return containsWord(normalized, "where")
                && hasRankingSignal(normalized);
    }

    public boolean isImplicitCategoryRankingQuestion(String query) {
        String normalized = normalize(query);
        boolean asksWhat = containsWord(normalized, "what");
        boolean hasSpendContext = containsAnyWord(normalized,
                "spend", "spending", "spent", "expense", "expenses",
                "pay", "paying", "paid", "purchase", "purchases", "bought");
        boolean hasObjectSignal = containsWord(normalized, "on") || containsWord(normalized, "for");
        return asksWhat && hasSpendContext && hasObjectSignal && hasRankingSignal(normalized);
    }

    public boolean hasSearchPhrase(String phrase) {
        return phrase != null && !phrase.isBlank();
    }

    public boolean isResolvablePhrase(String phrase) {
        if (!hasSearchPhrase(phrase)) {
            return false;
        }

        String normalized = normalize(phrase);
        if (normalized.length() < 3) {
            return false;
        }

        return !GENERIC_PHRASES.contains(normalized);
    }

    private boolean containsHint(String query, Set<String> hints) {
        String normalized = normalize(query);
        return Arrays.stream(normalized.split(" "))
                .anyMatch(hints::contains);
    }

    private boolean hasRankingSignal(String normalized) {
        return containsAnyWord(normalized, "top", "highest", "most", "lowest", "least");
    }

    private boolean containsAnyWord(String normalized, String... words) {
        return Arrays.stream(words).anyMatch(word -> containsWord(normalized, word));
    }

    private boolean containsWord(String normalized, String word) {
        return Arrays.stream(normalized.split(" "))
                .anyMatch(word::equals);
    }
}
