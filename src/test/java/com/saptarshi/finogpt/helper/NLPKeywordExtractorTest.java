package com.saptarshi.finogpt.helper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NLPKeywordExtractorTest {

    private final NLPKeywordExtractor extractor = new NLPKeywordExtractor();

    @Test
    void extractsMerchantPhraseWithoutTimeIntentOrLimitNoise() {
        String phrase = extractor.extractSearchPhrase("show me top 5 amazon pay transactions last month");

        assertEquals("amazon pay", phrase);
    }

    @Test
    void extractsCategoryPhraseFromExpenseQuery() {
        String phrase = extractor.extractSearchPhrase("food expenses past 3 months");

        assertEquals("food", phrase);
    }

    @Test
    void stripsLatestAndRecentListNoiseFromSearchPhrase() {
        String phrase = extractor.extractSearchPhrase("show latest 10 amazon pay transactions in april");

        assertEquals("amazon pay", phrase);
    }

    @Test
    void detectsEntityAndCategoryFocusedQueries() {
        assertTrue(extractor.isEntityFocused("top 5 merchants this month"));
        assertTrue(extractor.isCategoryFocused("food expenses past 3 months"));
        assertFalse(extractor.hasSearchPhrase(""));
        assertFalse(extractor.isResolvablePhrase("pay"));
        assertTrue(extractor.isResolvablePhrase("amazon pay"));
    }

    @Test
    void treatsWhereRankingQuestionsAsEntityFocusedWithoutSearchNoise() {
        String phrase = extractor.extractSearchPhrase("where am i spending most");

        assertEquals("", phrase);
        assertTrue(extractor.isImplicitEntityRankingQuestion("where am i spending most"));
    }

    @Test
    void treatsWhatSpendingOnQuestionsAsCategoryFocused() {
        String phrase = extractor.extractSearchPhrase("what am i spending most on");

        assertEquals("", phrase);
        assertTrue(extractor.isImplicitCategoryRankingQuestion("what am i spending most on"));
    }
}
