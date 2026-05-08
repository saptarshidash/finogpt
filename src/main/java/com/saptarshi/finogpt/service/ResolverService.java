package com.saptarshi.finogpt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saptarshi.finogpt.dto.QueryContext;
import com.saptarshi.finogpt.entity.Category;
import com.saptarshi.finogpt.entity.EntityTxn;
import com.saptarshi.finogpt.helper.NLPKeywordExtractor;
import com.saptarshi.finogpt.repository.CategoryRepository;
import com.saptarshi.finogpt.repository.EntityTxnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResolverService {

    private static final int TOP_MATCH_LIMIT = 3;
    private static final int MIN_ACCEPTABLE_SCORE = 60;
    private static final int AMBIGUITY_DELTA = 10;
    private static final int STRONG_SECONDARY_SCORE = 85;

    private static final Map<String, Integer> MONTH_MAP = Map.ofEntries(
            Map.entry("january", 1), Map.entry("february", 2),
            Map.entry("march", 3), Map.entry("april", 4),
            Map.entry("may", 5), Map.entry("june", 6),
            Map.entry("july", 7), Map.entry("august", 8),
            Map.entry("september", 9), Map.entry("october", 10),
            Map.entry("november", 11), Map.entry("december", 12),
            Map.entry("jan", 1), Map.entry("feb", 2),
            Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("jun", 6), Map.entry("jul", 7),
            Map.entry("aug", 8), Map.entry("sep", 9),
            Map.entry("sept", 9), Map.entry("oct", 10),
            Map.entry("nov", 11), Map.entry("dec", 12)
    );

    private final EntityTxnRepository entityRepo;
    private final CategoryRepository categoryRepo;
    private final LLMService llmService;
    private final NLPKeywordExtractor keywordExtractor;

    private static class EntityCandidate {
        private final EntityTxn entity;
        private final int score;

        private EntityCandidate(EntityTxn entity, int score) {
            this.entity = entity;
            this.score = score;
        }
    }

    private static class CategoryCandidate {
        private final Category category;
        private final int score;

        private CategoryCandidate(Category category, int score) {
            this.category = category;
            this.score = score;
        }
    }

    public QueryContext resolve(Long userId, String query) {
        QueryContext ctx = new QueryContext();
        ctx.setUserId(userId);
        ctx.setOriginalQuery(query);
        ctx.setRequestedDimension("NONE");
        ctx.setResolutionMode("NONE");

        String normalizedQuery = keywordExtractor.normalize(query);
        normalizedQuery = normalizeTimePhrases(normalizedQuery);

        extractTime(normalizedQuery, ctx);
        detectTransactionDirection(normalizedQuery, ctx);
        detectIntent(normalizedQuery, ctx);
        extractLimit(normalizedQuery, ctx);

        if (keywordExtractor.isEntityFocused(normalizedQuery)) {
            ctx.setRequestedDimension("ENTITY");
        } else if (keywordExtractor.isCategoryFocused(normalizedQuery)) {
            ctx.setRequestedDimension("CATEGORY");
        } else if (keywordExtractor.isImplicitEntityRankingQuestion(normalizedQuery)) {
            ctx.setRequestedDimension("ENTITY");
        } else if (keywordExtractor.isImplicitCategoryRankingQuestion(normalizedQuery)) {
            ctx.setRequestedDimension("CATEGORY");
        }

        String searchPhrase = keywordExtractor.extractSearchPhrase(normalizedQuery);
        ctx.setSearchPhrase(searchPhrase);
        resolveFilters(searchPhrase, normalizedQuery, ctx);

        if (needsLlmFallback(ctx, searchPhrase)) {
            fillWithLLMParser(query, ctx);

            if (!hasResolvedFilters(ctx)) {
                String fallbackPhrase = extractFallbackPhrase(ctx, searchPhrase);
                resolveFilters(fallbackPhrase, normalizedQuery, ctx);
            }
        }

        log.info("Final QueryContext: {}", ctx);
        return ctx;
    }

    private void extractTime(String query, QueryContext ctx) {
        LocalDate now = LocalDate.now();
        ctx.setYear(now.getYear());
        ctx.setTimeReferenced(hasTimeSignal(query));

        if (applyExplicitDateRange(query, ctx)) {
            return;
        }

        if (applyExplicitDate(query, ctx)) {
            return;
        }

        if (applyQuarterRange(query, ctx, now)) {
            return;
        }

        if (applyLastNDaysRange(query, ctx, now)) {
            return;
        }

        Matcher explicitYear = Pattern.compile("\\b(20\\d{2})\\b").matcher(query);
        if (explicitYear.find()) {
            ctx.setYear(Integer.parseInt(explicitYear.group(1)));
            ctx.setYearExplicit(true);
        }

        if (query.contains("today")) {
            ctx.setToday(true);
            ctx.setDay(now.getDayOfMonth());
            ctx.setMonth(now.getMonthValue());
            return;
        }

        if (query.contains("yesterday")) {
            LocalDate yesterday = now.minusDays(1);
            ctx.setYesterday(true);
            ctx.setDay(yesterday.getDayOfMonth());
            ctx.setMonth(yesterday.getMonthValue());
            ctx.setYear(yesterday.getYear());
            return;
        }

        if (query.contains("last month")) {
            LocalDate lastMonth = now.minusMonths(1);
            ctx.setLastMonth(true);
            ctx.setMonth(lastMonth.getMonthValue());
            ctx.setYear(lastMonth.getYear());
            return;
        }

        if (query.contains("this month")) {
            ctx.setThisMonth(true);
            ctx.setMonth(now.getMonthValue());
            return;
        }

        if (query.contains("this week")) {
            ctx.setThisWeek(true);
            return;
        }

        if (query.contains("last week")) {
            ctx.setLastWeek(true);
            return;
        }

        if (query.contains("this year")) {
            ctx.setYear(now.getYear());
            ctx.setYearExplicit(true);
            return;
        }

        if (query.contains("last year")) {
            ctx.setYear(now.minusYears(1).getYear());
            ctx.setYearExplicit(true);
            return;
        }

        Matcher lastNMonths = Pattern.compile("last (\\d+) months").matcher(query);
        if (lastNMonths.find()) {
            ctx.setLastNMonths(Integer.parseInt(lastNMonths.group(1)));
            return;
        }

        for (Map.Entry<String, Integer> monthEntry : MONTH_MAP.entrySet()) {
            if (query.contains(monthEntry.getKey())) {
                ctx.setMonth(monthEntry.getValue());
                return;
            }
        }

        if (ctx.isTimeReferenced()) {
            fillTimeWithLLM(query, ctx);
        }
    }

    private boolean applyExplicitDateRange(String query, QueryContext ctx) {
        Matcher matcher = Pattern.compile("\\b(?:from|between)\\s+(20\\d{2}-\\d{2}-\\d{2})\\s+(?:to|and)\\s+(20\\d{2}-\\d{2}-\\d{2})\\b").matcher(query);
        if (!matcher.find()) {
            return false;
        }

        LocalDate start = LocalDate.parse(matcher.group(1));
        LocalDate end = LocalDate.parse(matcher.group(2));
        if (end.isBefore(start)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }

        ctx.setFromDate(start);
        ctx.setToDate(end);
        ctx.setYear(start.getYear());
        ctx.setYearExplicit(true);
        ctx.setMonth(start.getMonthValue());
        return true;
    }

    private boolean applyExplicitDate(String query, QueryContext ctx) {
        Matcher matcher = Pattern.compile("\\b(20\\d{2}-\\d{2}-\\d{2})\\b").matcher(query);
        if (!matcher.find()) {
            return false;
        }

        LocalDate date = LocalDate.parse(matcher.group(1));
        ctx.setFromDate(date);
        ctx.setToDate(date);
        ctx.setYear(date.getYear());
        ctx.setYearExplicit(true);
        ctx.setMonth(date.getMonthValue());
        ctx.setDay(date.getDayOfMonth());
        return true;
    }

    private boolean applyQuarterRange(String query, QueryContext ctx, LocalDate now) {
        if (query.contains("this quarter")) {
            LocalDate start = quarterStart(now.getYear(), quarterOf(now.getMonthValue()));
            ctx.setFromDate(start);
            ctx.setToDate(now);
            ctx.setYear(now.getYear());
            ctx.setYearExplicit(true);
            return true;
        }

        if (query.contains("last quarter")) {
            LocalDate previousQuarterDate = now.minusMonths(3);
            int quarter = quarterOf(previousQuarterDate.getMonthValue());
            LocalDate start = quarterStart(previousQuarterDate.getYear(), quarter);
            LocalDate end = start.plusMonths(3).minusDays(1);
            ctx.setFromDate(start);
            ctx.setToDate(end);
            ctx.setYear(previousQuarterDate.getYear());
            ctx.setYearExplicit(true);
            return true;
        }

        Matcher yearQuarter = Pattern.compile("\\b(20\\d{2})\\s*q([1-4])\\b|\\bq([1-4])\\s*(20\\d{2})\\b").matcher(query);
        if (yearQuarter.find()) {
            Integer year = yearQuarter.group(1) != null ? Integer.parseInt(yearQuarter.group(1)) : Integer.parseInt(yearQuarter.group(4));
            Integer quarter = yearQuarter.group(2) != null ? Integer.parseInt(yearQuarter.group(2)) : Integer.parseInt(yearQuarter.group(3));
            LocalDate start = quarterStart(year, quarter);
            LocalDate end = start.plusMonths(3).minusDays(1);
            ctx.setFromDate(start);
            ctx.setToDate(end);
            ctx.setYear(year);
            ctx.setYearExplicit(true);
            return true;
        }

        Matcher quarterOnly = Pattern.compile("\\bq([1-4])\\b").matcher(query);
        if (!quarterOnly.find()) {
            return false;
        }

        Integer year = now.getYear();
        Integer quarter = Integer.parseInt(quarterOnly.group(1));
        LocalDate start = quarterStart(year, quarter);
        LocalDate end = start.plusMonths(3).minusDays(1);
        ctx.setFromDate(start);
        ctx.setToDate(end);
        ctx.setYear(year);
        ctx.setYearExplicit(true);
        return true;
    }

    private boolean applyLastNDaysRange(String query, QueryContext ctx, LocalDate now) {
        Matcher matcher = Pattern.compile("\\blast (\\d+) days\\b").matcher(query);
        if (!matcher.find()) {
            return false;
        }

        int days = Math.max(Integer.parseInt(matcher.group(1)), 1);
        ctx.setFromDate(now.minusDays(days - 1L));
        ctx.setToDate(now);
        ctx.setYear(ctx.getFromDate().getYear());
        ctx.setYearExplicit(true);
        return true;
    }

    private void fillTimeWithLLM(String query, QueryContext ctx) {
        String prompt = String.format(
                "You are a date parser.%n%n" +
                        "Convert the user's query into structured time fields.%n%n" +
                        "Today is: %s%n%n" +
                        "Extract:%n" +
                        "- year%n" +
                        "- month%n" +
                        "- day (optional)%n" +
                        "- lastNMonths (optional)%n%n" +
                        "Return JSON ONLY:%n" +
                        "{%n" +
                        "  \"year\": 2026,%n" +
                        "  \"month\": 5,%n" +
                        "  \"day\": null,%n" +
                        "  \"lastNMonths\": null%n" +
                        "}%n%n" +
                        "Query:%n" +
                        "%s",
                LocalDate.now(),
                query
        );

        try {
            Map<String, Object> json = parseJson(llmService.generate(prompt));

            if (json.get("year") != null) {
                ctx.setYear((Integer) json.get("year"));
                ctx.setYearExplicit(true);
            }
            if (json.get("month") != null) {
                ctx.setMonth((Integer) json.get("month"));
            }
            if (json.get("day") != null) {
                ctx.setDay((Integer) json.get("day"));
            }
            if (json.get("lastNMonths") != null) {
                ctx.setLastNMonths((Integer) json.get("lastNMonths"));
            }

            log.info("LLM resolved time: {}", json);
        } catch (Exception ex) {
            log.warn("LLM time parsing failed, fallback to default");
        }
    }

    private void fillWithLLMParser(String query, QueryContext ctx) {
        String prompt = String.format(
                "You are a financial query parser.%n%n" +
                        "Convert the user query into structured JSON.%n%n" +
                        "Today is: %s%n%n" +
                        "Extract:%n" +
                        "- intent (SUM, COUNT, AVG, TOP, MAX, MIN)%n" +
                        "- entity (merchant name if present)%n" +
                        "- category (if present)%n" +
                        "- txnDirection (DEBIT, CREDIT, or null)%n" +
                        "- dimension (ENTITY, CATEGORY, or NONE)%n" +
                        "- limit (number if present)%n" +
                        "- year%n" +
                        "- month%n" +
                        "- day%n" +
                        "- lastNMonths%n%n" +
                        "Rules:%n" +
                        "- Do NOT guess IDs%n" +
                        "- Keep entity/category as text%n" +
                        "- Return ONLY JSON%n" +
                        "- If not present -> null%n%n" +
                        "Example:%n" +
                        "Query: \"top 5 zomato transactions last month\"%n" +
                        "Output:%n" +
                        "{%n" +
                        "  \"intent\": \"TOP\",%n" +
                        "  \"entity\": \"zomato\",%n" +
                        "  \"category\": null,%n" +
                        "  \"txnDirection\": \"DEBIT\",%n" +
                        "  \"dimension\": \"ENTITY\",%n" +
                        "  \"limit\": 5,%n" +
                        "  \"year\": 2026,%n" +
                        "  \"month\": 3,%n" +
                        "  \"day\": null,%n" +
                        "  \"lastNMonths\": null%n" +
                        "}%n%n" +
                        "Query: \"how much did i receive from diganta\"%n" +
                        "Output:%n" +
                        "{%n" +
                        "  \"intent\": \"SUM\",%n" +
                        "  \"entity\": \"diganta\",%n" +
                        "  \"category\": null,%n" +
                        "  \"txnDirection\": \"CREDIT\",%n" +
                        "  \"dimension\": \"ENTITY\",%n" +
                        "  \"limit\": null,%n" +
                        "  \"year\": null,%n" +
                        "  \"month\": null,%n" +
                        "  \"day\": null,%n" +
                        "  \"lastNMonths\": null%n" +
                        "}%n%n" +
                        "Query: \"how much did i spend this year in total\"%n" +
                        "Output:%n" +
                        "{%n" +
                        "  \"intent\": \"SUM\",%n" +
                        "  \"entity\": null,%n" +
                        "  \"category\": null,%n" +
                        "  \"txnDirection\": \"DEBIT\",%n" +
                        "  \"dimension\": \"NONE\",%n" +
                        "  \"limit\": null,%n" +
                        "  \"year\": 2026,%n" +
                        "  \"month\": null,%n" +
                        "  \"day\": null,%n" +
                        "  \"lastNMonths\": null%n" +
                        "}%n%n" +
                        "Query: \"maximum amount of transaction i made this year\"%n" +
                        "Output:%n" +
                        "{%n" +
                        "  \"intent\": \"MAX\",%n" +
                        "  \"entity\": null,%n" +
                        "  \"category\": null,%n" +
                        "  \"txnDirection\": null,%n" +
                        "  \"dimension\": \"NONE\",%n" +
                        "  \"limit\": null,%n" +
                        "  \"year\": 2026,%n" +
                        "  \"month\": null,%n" +
                        "  \"day\": null,%n" +
                        "  \"lastNMonths\": null%n" +
                        "}%n%n" +
                        "Query: \"to whom i made maximum amount of transaction this year\"%n" +
                        "Output:%n" +
                        "{%n" +
                        "  \"intent\": \"MAX\",%n" +
                        "  \"entity\": null,%n" +
                        "  \"category\": null,%n" +
                        "  \"txnDirection\": \"DEBIT\",%n" +
                        "  \"dimension\": \"ENTITY\",%n" +
                        "  \"limit\": null,%n" +
                        "  \"year\": 2026,%n" +
                        "  \"month\": null,%n" +
                        "  \"day\": null,%n" +
                        "  \"lastNMonths\": null%n" +
                        "}%n%n" +
                        "Query:%n" +
                        "%s",
                LocalDate.now(),
                query
        );

        try {
            Map<String, Object> json = parseJson(llmService.generate(prompt));
            mergeIntoContext(json, ctx);
            log.info("LLM full parse result: {}", json);
        } catch (Exception ex) {
            log.warn("LLM parsing failed: {}", ex.getMessage());
        }
    }

    private Map<String, Object> parseJson(String response) {
        String cleaned = response.replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        try {
            return new ObjectMapper().readValue(cleaned, Map.class);
        } catch (Exception ex) {
            throw new RuntimeException("Invalid JSON from LLM: " + cleaned);
        }
    }

    private void mergeIntoContext(Map<String, Object> json, QueryContext ctx) {
        if (json.get("intent") != null) {
            ctx.setIntent((String) json.get("intent"));
        }
        if (json.get("entity") != null) {
            ctx.setRawEntity((String) json.get("entity"));
        }
        if (json.get("category") != null) {
            ctx.setRawCategory((String) json.get("category"));
        }
        if (json.get("limit") != null) {
            ctx.setLimit((Integer) json.get("limit"));
        }
        if (json.get("year") != null) {
            ctx.setYear((Integer) json.get("year"));
            ctx.setYearExplicit(true);
        }
        if (json.get("month") != null) {
            ctx.setMonth((Integer) json.get("month"));
        }
        if (json.get("day") != null) {
            ctx.setDay((Integer) json.get("day"));
        }
        if (json.get("lastNMonths") != null) {
            ctx.setLastNMonths((Integer) json.get("lastNMonths"));
        }
        if (json.get("txnDirection") != null) {
            ctx.setTxnDirection(((String) json.get("txnDirection")).toUpperCase());
        }
        if (json.get("dimension") != null) {
            ctx.setRequestedDimension(((String) json.get("dimension")).toUpperCase());
        }
    }

    private void resolveFilters(String searchPhrase, String query, QueryContext ctx) {
        if (!keywordExtractor.isResolvablePhrase(searchPhrase)) {
            clearResolution(ctx);
            return;
        }

        boolean entityFocused = keywordExtractor.isEntityFocused(query);
        boolean categoryFocused = keywordExtractor.isCategoryFocused(query);

        if (entityFocused) {
            ctx.setRequestedDimension("ENTITY");
        } else if (categoryFocused) {
            ctx.setRequestedDimension("CATEGORY");
        }

        List<EntityCandidate> entityMatches = rankEntityMatches(searchPhrase);
        List<CategoryCandidate> categoryMatches = rankCategoryMatches(searchPhrase);

        storeCandidateMetadata(entityMatches, categoryMatches, ctx);

        if (entityFocused && !categoryFocused && !entityMatches.isEmpty()) {
            ctx.setRawEntity(searchPhrase);
            applyEntityMatches(entityMatches, ctx);
            return;
        }

        if (categoryFocused && !entityFocused && !categoryMatches.isEmpty()) {
            ctx.setRawCategory(searchPhrase);
            applyCategoryMatches(categoryMatches, ctx);
            return;
        }

        if (!entityMatches.isEmpty() && categoryMatches.isEmpty()) {
            ctx.setRawEntity(searchPhrase);
            applyEntityMatches(entityMatches, ctx);
            return;
        }

        if (!categoryMatches.isEmpty() && entityMatches.isEmpty()) {
            ctx.setRawCategory(searchPhrase);
            applyCategoryMatches(categoryMatches, ctx);
            return;
        }

        if (!categoryMatches.isEmpty() && !entityMatches.isEmpty()) {
            String normalizedSearchPhrase = searchPhrase.trim().toLowerCase();
            int bestEntityScore = entityMatches.get(0).score;
            int bestCategoryScore = categoryMatches.get(0).score;

            if (Math.abs(bestCategoryScore - bestEntityScore) <= AMBIGUITY_DELTA) {
                ctx.setAmbiguousResolution(true);
            }

            if (bestCategoryScore > bestEntityScore) {
                ctx.setRawCategory(searchPhrase);
                applyCategoryMatches(categoryMatches, ctx);
            } else {
                ctx.setRawEntity(searchPhrase);
                applyEntityMatches(entityMatches, ctx);
            }
            return;
        }

        clearResolution(ctx);
    }

    private void resolveEntity(String keyword, QueryContext ctx) {
        List<EntityCandidate> matches = rankEntityMatches(keyword);
        storeCandidateMetadata(matches, List.of(), ctx);
        applyEntityMatches(matches, ctx);
    }

    private void resolveCategory(String keyword, QueryContext ctx) {
        List<CategoryCandidate> matches = rankCategoryMatches(keyword);
        storeCandidateMetadata(List.of(), matches, ctx);
        applyCategoryMatches(matches, ctx);
    }

    private List<EntityCandidate> rankEntityMatches(String keyword) {
        String normalizedKeyword = keyword.trim().toLowerCase();
        String normalizedEntityKeyword = normalizeEntityKeyword(normalizedKeyword);

        return entityRepo.searchFuzzy(normalizedKeyword).stream()
                .map(entity -> new EntityCandidate(entity, scoreEntity(entity, normalizedEntityKeyword)))
                .filter(candidate -> candidate.score >= MIN_ACCEPTABLE_SCORE)
                .sorted(Comparator.comparingInt((EntityCandidate candidate) -> candidate.score)
                        .reversed()
                        .thenComparing(candidate -> candidate.entity.getName().length()))
                .limit(TOP_MATCH_LIMIT)
                .toList();
    }

    private List<CategoryCandidate> rankCategoryMatches(String keyword) {
        String normalizedKeyword = keyword.trim().toLowerCase();
        List<Category> candidates = categoryRepo.searchFuzzy(normalizedKeyword);

        if (candidates.isEmpty()) {
            candidates = categoryRepo.findAll();
        }

        return candidates.stream()
                .map(category -> new CategoryCandidate(category, scoreCategory(category, normalizedKeyword)))
                .filter(candidate -> candidate.score >= MIN_ACCEPTABLE_SCORE)
                .sorted(Comparator.comparingInt((CategoryCandidate candidate) -> candidate.score)
                        .reversed()
                        .thenComparing(candidate -> candidate.category.getName().length()))
                .limit(TOP_MATCH_LIMIT)
                .toList();
    }

    private int scoreEntity(EntityTxn entity, String keyword) {
        String normalizedName = entity.getNormalizedName() != null
                ? entity.getNormalizedName().toLowerCase()
                : keywordExtractor.normalize(entity.getName());

        String lowerName = entity.getName().toLowerCase();
        if (normalizedName.equals(keyword)) {
            return 100;
        }
        if (lowerName.equals(keyword)) {
            return 95;
        }
        if (normalizedName.startsWith(keyword) || lowerName.startsWith(keyword)) {
            return 85;
        }
        if (normalizedName.contains(keyword) || lowerName.contains(keyword)) {
            return 65 + tokenOverlapScore(normalizedName, keyword);
        }
        return tokenOverlapScore(normalizedName, keyword) >= 2 ? 55 : 10;
    }

    private int scoreCategory(Category category, String keyword) {
        String normalizedName = keywordExtractor.normalize(category.getName());
        if (normalizedName.equals(keyword)) {
            return 100;
        }
        if (normalizedName.startsWith(keyword)) {
            return 85;
        }
        if (normalizedName.contains(keyword)) {
            return 65 + tokenOverlapScore(normalizedName, keyword);
        }

        int stemScore = tokenStemScore(normalizedName, keyword);
        if (stemScore > 0) {
            return 60 + stemScore;
        }

        int similarityScore = tokenSimilarityScore(normalizedName, keyword);
        if (similarityScore > 0) {
            return 60 + similarityScore;
        }

        return tokenOverlapScore(normalizedName, keyword) >= 2 ? 55 : 10;
    }

    private int tokenOverlapScore(String candidate, String keyword) {
        List<String> keywordTokens = List.of(keyword.split("\\s+"));
        int overlap = 0;
        for (String token : keywordTokens) {
            if (!token.isBlank() && candidate.contains(token)) {
                overlap++;
            }
        }
        return overlap * 5;
    }

    private int tokenStemScore(String candidate, String keyword) {
        List<String> candidateTokens = List.of(candidate.split("\\s+"));
        List<String> keywordTokens = List.of(keyword.split("\\s+"));
        int bestScore = 0;

        for (String candidateToken : candidateTokens) {
            if (candidateToken.isBlank()) {
                continue;
            }

            for (String keywordToken : keywordTokens) {
                if (keywordToken.isBlank()) {
                    continue;
                }

                int prefixLength = commonPrefixLength(candidateToken, keywordToken);
                if (prefixLength >= 5) {
                    bestScore = Math.max(bestScore, Math.min(prefixLength * 2, 20));
                }
            }
        }

        return bestScore;
    }

    private int tokenSimilarityScore(String candidate, String keyword) {
        List<String> candidateTokens = List.of(candidate.split("\\s+"));
        List<String> keywordTokens = List.of(keyword.split("\\s+"));
        int bestScore = 0;

        for (String candidateToken : candidateTokens) {
            if (candidateToken.isBlank()) {
                continue;
            }

            for (String keywordToken : keywordTokens) {
                if (keywordToken.isBlank()) {
                    continue;
                }

                int distance = levenshteinDistance(candidateToken, keywordToken);
                int maxLength = Math.max(candidateToken.length(), keywordToken.length());
                if (maxLength == 0) {
                    continue;
                }

                double similarity = 1.0 - ((double) distance / maxLength);
                if (similarity >= 0.60d) {
                    bestScore = Math.max(bestScore, (int) Math.round(similarity * 20));
                }
            }
        }

        return bestScore;
    }

    private int commonPrefixLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int index = 0;

        while (index < limit && left.charAt(index) == right.charAt(index)) {
            index++;
        }

        return index;
    }

    private int levenshteinDistance(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];

        for (int i = 0; i <= left.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= right.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[left.length()][right.length()];
    }

    private void applyEntityMatches(List<EntityCandidate> matches, QueryContext ctx) {
        if (matches.isEmpty()) {
            clearEntityResolution(ctx);
            return;
        }

        EntityCandidate primary = matches.get(0);
        ctx.setEntityIds(List.of(primary.entity.getId()));
        ctx.setEntityNames(List.of(primary.entity.getName()));
        ctx.setEntityId(primary.entity.getId());
        ctx.setEntityName(primary.entity.getName());
        ctx.setAmbiguousEntity(isAmbiguous(matches.stream().map(candidate -> candidate.score).toList()));
        ctx.setAmbiguousResolution(ctx.isAmbiguousResolution() || ctx.isAmbiguousEntity());
        ctx.setResolutionMode("ENTITY");
        clearCategoryResolution(ctx);
        log.info("Resolved ENTITY primary match: {}, candidates: {}",
                primary.entity.getName(),
                matches.stream().map(candidate -> candidate.entity.getName()).toList());
    }

    private void applyCategoryMatches(List<CategoryCandidate> matches, QueryContext ctx) {
        if (matches.isEmpty()) {
            clearCategoryResolution(ctx);
            return;
        }

        CategoryCandidate primary = matches.get(0);
        ctx.setCategoryIds(List.of(primary.category.getId()));
        ctx.setCategoryNames(List.of(primary.category.getName()));
        ctx.setCategoryId(primary.category.getId());
        ctx.setCategoryName(primary.category.getName());
        ctx.setAmbiguousCategory(isAmbiguous(matches.stream().map(candidate -> candidate.score).toList()));
        ctx.setAmbiguousResolution(ctx.isAmbiguousResolution() || ctx.isAmbiguousCategory());
        ctx.setResolutionMode("CATEGORY");
        clearEntityResolution(ctx);
        log.info("Resolved CATEGORY primary match: {}, candidates: {}",
                primary.category.getName(),
                matches.stream().map(candidate -> candidate.category.getName()).toList());
    }

    private void storeCandidateMetadata(List<EntityCandidate> entityMatches,
                                        List<CategoryCandidate> categoryMatches,
                                        QueryContext ctx) {
        ctx.setEntityCandidateIds(entityMatches.stream().map(candidate -> candidate.entity.getId()).toList());
        ctx.setEntityCandidateNames(entityMatches.stream().map(candidate -> candidate.entity.getName()).toList());
        ctx.setEntityCandidateScores(entityMatches.stream().map(candidate -> candidate.score).toList());
        ctx.setCategoryCandidateIds(categoryMatches.stream().map(candidate -> candidate.category.getId()).toList());
        ctx.setCategoryCandidateNames(categoryMatches.stream().map(candidate -> candidate.category.getName()).toList());
        ctx.setCategoryCandidateScores(categoryMatches.stream().map(candidate -> candidate.score).toList());
    }

    private boolean isAmbiguous(List<Integer> scores) {
        if (scores == null || scores.size() < 2) {
            return false;
        }

        return scores.get(0) - scores.get(1) <= AMBIGUITY_DELTA
                || scores.get(1) >= STRONG_SECONDARY_SCORE;
    }

    private void clearResolution(QueryContext ctx) {
        clearEntityResolution(ctx);
        clearCategoryResolution(ctx);
        ctx.setEntityCandidateNames(List.of());
        ctx.setEntityCandidateScores(List.of());
        ctx.setEntityCandidateIds(List.of());
        ctx.setCategoryCandidateNames(List.of());
        ctx.setCategoryCandidateScores(List.of());
        ctx.setCategoryCandidateIds(List.of());
        ctx.setAmbiguousResolution(false);
        ctx.setResolutionMode("NONE");
    }

    private void clearEntityResolution(QueryContext ctx) {
        ctx.setEntityId(null);
        ctx.setEntityName(null);
        ctx.setEntityIds(null);
        ctx.setEntityNames(null);
        ctx.setAmbiguousEntity(false);
    }

    private void clearCategoryResolution(QueryContext ctx) {
        ctx.setCategoryId(null);
        ctx.setCategoryName(null);
        ctx.setCategoryIds(null);
        ctx.setCategoryNames(null);
        ctx.setAmbiguousCategory(false);
    }

    private void extractLimit(String query, QueryContext ctx) {
        Matcher matcher = Pattern.compile("\\b(?:top|latest|recent|first)\\s+(\\d+)\\b|\\bshow\\s+(\\d+)\\b|\\blast\\s+(\\d+)\\s+(?:transactions|transaction|payments|payment|records|record|debits|credits)\\b").matcher(query);
        if (matcher.find()) {
            for (int index = 1; index <= matcher.groupCount(); index++) {
                if (matcher.group(index) != null) {
                    ctx.setLimit(Integer.parseInt(matcher.group(index)));
                    return;
                }
            }
        }

        if ("TOP".equalsIgnoreCase(ctx.getIntent())) {
            ctx.setLimit(5);
            return;
        }

        if ("LIST".equalsIgnoreCase(ctx.getIntent())) {
            ctx.setLimit(20);
        }
    }

    private void detectIntent(String query, QueryContext ctx) {
        if (isMaxTransactionQuery(query)) {
            ctx.setIntent("MAX");
            return;
        }

        if (isMinTransactionQuery(query)) {
            ctx.setIntent("MIN");
            return;
        }

        if (query.contains("top") || query.contains("highest") || query.contains("most")) {
            ctx.setIntent("TOP");
            return;
        }

        if (query.contains("total") || query.contains("sum")
                || query.contains("spend") || query.contains("spent")
                || query.contains("expense") || query.contains("expenses")
                || query.contains("receive") || query.contains("received")
                || query.contains("income") || query.contains("salary")
                || query.contains("refund") || query.contains("refunded")
                || query.contains("earn") || query.contains("earned")
                || query.contains("credited")) {
            ctx.setIntent("SUM");
            return;
        }

        if (query.contains("count") || query.contains("how many")) {
            ctx.setIntent("COUNT");
            return;
        }

        if (query.contains("average") || query.contains("avg")) {
            ctx.setIntent("AVG");
            return;
        }

        if (isListQuery(query) || isTransactionOnlyQuery(query)) {
            ctx.setIntent("LIST");
            return;
        }

        if (query.contains("credit") || query.contains("credits")
                || query.contains("debit") || query.contains("debits")) {
            ctx.setIntent("SUM");
            return;
        }

        ctx.setIntent("UNKNOWN");
    }

    private boolean isListQuery(String query) {
        boolean listSignal = query.contains("show")
                || query.contains("list")
                || query.contains("get")
                || query.contains("latest")
                || query.contains("recent");
        return listSignal && isTransactionOnlyQuery(query);
    }

    private boolean isTransactionOnlyQuery(String query) {
        return query.contains("transaction")
                || query.contains("transactions")
                || query.contains("payment")
                || query.contains("payments")
                || query.contains("record")
                || query.contains("records")
                || query.matches(".*\\b(debit|debits|credit|credits)\\b.*");
    }

    private boolean isMaxTransactionQuery(String query) {
        boolean hasMaxSignal = query.contains("maximum")
                || query.contains("highest")
                || query.contains("largest")
                || query.contains("max ");
        boolean transactionScoped = query.contains("transaction")
                || query.contains("transactions")
                || query.contains("payment")
                || query.contains("payments")
                || query.contains("amount");
        boolean dimensionRanking = query.contains("category")
                || query.contains("categories")
                || query.contains("merchant")
                || query.contains("merchants")
                || query.contains("vendor")
                || query.contains("vendors");
        return hasMaxSignal && transactionScoped && !dimensionRanking;
    }

    private boolean isMinTransactionQuery(String query) {
        boolean hasMinSignal = query.contains("minimum")
                || query.contains("lowest")
                || query.contains("smallest")
                || query.contains("min ");
        boolean transactionScoped = query.contains("transaction")
                || query.contains("transactions")
                || query.contains("payment")
                || query.contains("payments")
                || query.contains("amount");
        boolean dimensionRanking = query.contains("category")
                || query.contains("categories")
                || query.contains("merchant")
                || query.contains("merchants")
                || query.contains("vendor")
                || query.contains("vendors");
        return hasMinSignal && transactionScoped && !dimensionRanking;
    }

    private void detectTransactionDirection(String query, QueryContext ctx) {
        if (query.contains("credit") || query.contains("credits")) {
            ctx.setTxnDirection("CREDIT");
            return;
        }

        if (query.contains("receive") || query.contains("received")
                || query.contains("income") || query.contains("salary")
                || query.contains("refund") || query.contains("refunds")
                || query.contains("refunded") || query.contains("credited")
                || query.contains("earn") || query.contains("earned")) {
            ctx.setTxnDirection("CREDIT");
            return;
        }

        if (query.contains("debit") || query.contains("debits")
                || query.contains("spend") || query.contains("spent")
                || query.contains("expense") || query.contains("expenses")
                || query.contains("paid") || query.contains("purchase")
                || query.contains("purchases") || query.contains("bought")) {
            ctx.setTxnDirection("DEBIT");
        }
    }

    private boolean needsLlmFallback(QueryContext ctx, String searchPhrase) {
        boolean missingIntent = ctx.getIntent() == null || "UNKNOWN".equalsIgnoreCase(ctx.getIntent());
        boolean missingTime = ctx.getMonth() == null
                && ctx.getLastNMonths() == null
                && !ctx.isToday()
                && !ctx.isYesterday()
                && !ctx.isYearExplicit()
                && ctx.isTimeReferenced();
        boolean unresolvedSearchPhrase = keywordExtractor.isResolvablePhrase(searchPhrase) && !hasResolvedFilters(ctx);

        return missingIntent || missingTime || unresolvedSearchPhrase;
    }

    private boolean hasResolvedFilters(QueryContext ctx) {
        return (ctx.getEntityIds() != null && !ctx.getEntityIds().isEmpty())
                || (ctx.getCategoryIds() != null && !ctx.getCategoryIds().isEmpty());
    }

    private String extractFallbackPhrase(QueryContext ctx, String searchPhrase) {
        if (ctx.getRawEntity() != null && !ctx.getRawEntity().isBlank()) {
            return ctx.getRawEntity();
        }
        if (ctx.getRawCategory() != null && !ctx.getRawCategory().isBlank()) {
            return ctx.getRawCategory();
        }
        return searchPhrase;
    }

    private String normalizeTimePhrases(String query) {
        String normalized = query;
        normalized = normalized.replaceAll("past month", "last month");
        normalized = normalized.replaceAll("previous month", "last month");
        normalized = normalized.replaceAll("last 1 month", "last month");
        normalized = normalized.replaceAll("past day", "yesterday");
        normalized = normalized.replaceAll("previous day", "yesterday");
        normalized = normalized.replaceAll("past week", "last week");
        normalized = normalized.replaceAll("previous week", "last week");
        normalized = normalized.replaceAll("past year", "last year");
        normalized = normalized.replaceAll("previous year", "last year");
        normalized = normalized.replaceAll("past quarter", "last quarter");
        normalized = normalized.replaceAll("previous quarter", "last quarter");
        normalized = normalized.replaceAll("past (\\d+) months", "last $1 months");
        normalized = normalized.replaceAll("previous (\\d+) months", "last $1 months");
        normalized = normalized.replaceAll("past (\\d+) days", "last $1 days");
        normalized = normalized.replaceAll("previous (\\d+) days", "last $1 days");
        normalized = normalized.replaceAll("past 30 days", "last month");
        return normalized;
    }

    private boolean hasTimeSignal(String query) {
        return query.contains("today")
                || query.contains("yesterday")
                || query.contains("last ")
                || query.contains("this ")
                || query.contains("past ")
                || query.contains("previous ")
                || query.contains("month")
                || query.contains("months")
                || query.contains("week")
                || query.contains("year")
                || query.contains("day")
                || query.contains("days")
                || query.contains("quarter")
                || query.matches(".*\\bq[1-4]\\b.*")
                || Pattern.compile("\\b20\\d{2}\\b").matcher(query).find();
    }

    private String normalizeEntityKeyword(String keyword) {
        return keyword.replaceAll("\\b(pvt ltd|private limited|ltd|limited|services|technologies|tech|solutions|india)\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int quarterOf(int month) {
        return ((month - 1) / 3) + 1;
    }

    private LocalDate quarterStart(int year, int quarter) {
        int startMonth = ((quarter - 1) * 3) + 1;
        return LocalDate.of(year, Month.of(startMonth), 1);
    }
}
