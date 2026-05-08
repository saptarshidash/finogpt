package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.dto.QueryContext;
import com.saptarshi.finogpt.entity.Category;
import com.saptarshi.finogpt.entity.EntityTxn;
import com.saptarshi.finogpt.helper.NLPKeywordExtractor;
import com.saptarshi.finogpt.repository.CategoryRepository;
import com.saptarshi.finogpt.repository.EntityTxnRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolverServiceTest {

    private final EntityTxnRepository entityRepository = Mockito.mock(EntityTxnRepository.class);
    private final CategoryRepository categoryRepository = Mockito.mock(CategoryRepository.class);
    private final LLMService llmService = Mockito.mock(LLMService.class);
    private final ResolverService resolverService =
            new ResolverService(entityRepository, categoryRepository, llmService, new NLPKeywordExtractor());

    @Test
    void resolvesEntityQueryDeterministically() {
        EntityTxn amazonPay = new EntityTxn();
        amazonPay.setId(10L);
        amazonPay.setName("Amazon Pay India");
        amazonPay.setNormalizedName("amazon pay");

        EntityTxn amazonServices = new EntityTxn();
        amazonServices.setId(11L);
        amazonServices.setName("Amazon Services");
        amazonServices.setNormalizedName("amazon");

        Mockito.when(entityRepository.searchFuzzy("amazon pay"))
                .thenReturn(List.of(amazonPay, amazonServices));
        Mockito.when(categoryRepository.searchFuzzy("amazon pay"))
                .thenReturn(List.of());

        QueryContext ctx = resolverService.resolve(1L, "total amazon pay transactions last month");

        assertEquals("SUM", ctx.getIntent());
        assertEquals(List.of(10L), ctx.getEntityIds());
        assertEquals(List.of(10L, 11L), ctx.getEntityCandidateIds());
        assertNull(ctx.getCategoryIds());
        assertEquals("amazon pay", ctx.getRawEntity());
        assertEquals("ENTITY", ctx.getResolutionMode());
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void resolvesRowLevelMerchantTransactionQueryWithoutForcingDimensionAggregation() {
        EntityTxn amazonPay = new EntityTxn();
        amazonPay.setId(10L);
        amazonPay.setName("Amazon Pay India");
        amazonPay.setNormalizedName("amazon pay");

        Mockito.when(entityRepository.searchFuzzy("amazon pay"))
                .thenReturn(List.of(amazonPay));
        Mockito.when(categoryRepository.searchFuzzy("amazon pay"))
                .thenReturn(List.of());

        QueryContext ctx = resolverService.resolve(1L, "amazon pay transactions last month");

        assertEquals("LIST", ctx.getIntent());
        assertEquals(List.of(10L), ctx.getEntityIds());
        assertEquals("ENTITY", ctx.getResolutionMode());
        assertEquals("NONE", ctx.getRequestedDimension());
    }

    @Test
    void resolvesCategoryExpenseQueryWithoutLlmFallback() {
        Category food = new Category();
        food.setId(5L);
        food.setName("Food");

        Mockito.when(categoryRepository.searchFuzzy("food"))
                .thenReturn(List.of(food));
        Mockito.when(entityRepository.searchFuzzy("food"))
                .thenReturn(List.of());

        QueryContext ctx = resolverService.resolve(2L, "food expenses past 3 months");

        assertEquals("SUM", ctx.getIntent());
        assertEquals(Integer.valueOf(3), ctx.getLastNMonths());
        assertEquals(List.of(5L), ctx.getCategoryIds());
        assertEquals("food", ctx.getRawCategory());
        assertEquals("CATEGORY", ctx.getResolutionMode());
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void leavesUnfilteredTopQueryDeterministic() {
        QueryContext ctx = resolverService.resolve(3L, "top 5 merchants this month");

        assertEquals("TOP", ctx.getIntent());
        assertEquals(Integer.valueOf(5), ctx.getLimit());
        assertEquals(Integer.valueOf(java.time.LocalDate.now().getMonthValue()), ctx.getMonth());
        assertNull(ctx.getEntityIds());
        assertNull(ctx.getCategoryIds());
        assertEquals("NONE", ctx.getResolutionMode());
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void flagsAmbiguousEntityResolutionAndKeepsTopMatches() {
        EntityTxn amazon = new EntityTxn();
        amazon.setId(20L);
        amazon.setName("Amazon");
        amazon.setNormalizedName("amazon");

        EntityTxn amazonPay = new EntityTxn();
        amazonPay.setId(21L);
        amazonPay.setName("Amazon Pay India");
        amazonPay.setNormalizedName("amazon pay");

        EntityTxn amazonSeller = new EntityTxn();
        amazonSeller.setId(22L);
        amazonSeller.setName("Amazon Seller Services");
        amazonSeller.setNormalizedName("amazon seller");

        Mockito.when(entityRepository.searchFuzzy("amazon"))
                .thenReturn(List.of(amazon, amazonPay, amazonSeller));
        Mockito.when(categoryRepository.searchFuzzy("amazon"))
                .thenReturn(List.of());

        QueryContext ctx = resolverService.resolve(4L, "amazon transactions last month");

        assertEquals("ENTITY", ctx.getResolutionMode());
        assertEquals(List.of(20L), ctx.getEntityIds());
        assertEquals(List.of("Amazon", "Amazon Pay India", "Amazon Seller Services"), ctx.getEntityCandidateNames());
        assertTrue(ctx.isAmbiguousEntity());
        assertTrue(ctx.isAmbiguousResolution());
    }

    @Test
    void treatsStrongSecondaryPrefixMatchesAsAmbiguous() {
        EntityTxn mou = new EntityTxn();
        mou.setId(62L);
        mou.setName("Mou");
        mou.setNormalizedName("mou");

        EntityTxn moumita = new EntityTxn();
        moumita.setId(319L);
        moumita.setName("Moumita");
        moumita.setNormalizedName("moumita");

        EntityTxn moulesh = new EntityTxn();
        moulesh.setId(446L);
        moulesh.setName("MOULESH");
        moulesh.setNormalizedName("moulesh");

        Mockito.when(entityRepository.searchFuzzy("mou"))
                .thenReturn(List.of(mou, moumita, moulesh));
        Mockito.when(categoryRepository.searchFuzzy("mou"))
                .thenReturn(List.of());

        QueryContext ctx = resolverService.resolve(15L, "how much did i spend on mou last year");

        assertEquals(List.of(62L), ctx.getEntityIds());
        assertEquals(List.of(62L, 319L, 446L), ctx.getEntityCandidateIds());
        assertTrue(ctx.isAmbiguousEntity());
        assertTrue(ctx.isAmbiguousResolution());
    }

    @Test
    void ignoresGenericSearchPhraseInsteadOfGuessing() {
        QueryContext ctx = resolverService.resolve(5L, "pay transactions last month");

        assertEquals("SUM", ctx.getIntent());
        assertEquals("pay", ctx.getSearchPhrase());
        assertEquals("NONE", ctx.getResolutionMode());
        assertNull(ctx.getEntityIds());
        assertNull(ctx.getCategoryIds());
        assertFalse(ctx.isAmbiguousResolution());
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void keepsTotalSpendQueriesUnfilteredAndConstrainsThisYear() {
        QueryContext ctx = resolverService.resolve(6L, "how much did i spend this year in total?");

        assertEquals("SUM", ctx.getIntent());
        assertEquals("DEBIT", ctx.getTxnDirection());
        assertEquals("NONE", ctx.getRequestedDimension());
        assertEquals("NONE", ctx.getResolutionMode());
        assertTrue(ctx.isYearExplicit());
        assertEquals(Integer.valueOf(java.time.LocalDate.now().getYear()), ctx.getYear());
        assertTrue(ctx.getSearchPhrase() == null || ctx.getSearchPhrase().isBlank());
        assertNull(ctx.getEntityIds());
        assertNull(ctx.getCategoryIds());
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void resolvesCreditEntityQueriesFromNaturalLanguage() {
        EntityTxn diganta = new EntityTxn();
        diganta.setId(31L);
        diganta.setName("Diganta");
        diganta.setNormalizedName("diganta");

        Mockito.when(entityRepository.searchFuzzy("diganta"))
                .thenReturn(List.of(diganta));
        Mockito.when(categoryRepository.searchFuzzy("diganta"))
                .thenReturn(List.of());

        QueryContext ctx = resolverService.resolve(7L, "how much did i receive from diganta");

        assertEquals("SUM", ctx.getIntent());
        assertEquals("CREDIT", ctx.getTxnDirection());
        assertEquals(List.of(31L), ctx.getEntityIds());
        assertEquals("diganta", ctx.getRawEntity());
        assertEquals("ENTITY", ctx.getResolutionMode());
        assertNull(ctx.getCategoryIds());
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void keepsTopCategoryThisWeekQueriesDimensionOnly() {
        QueryContext ctx = resolverService.resolve(8L, "Which category had the highest debit this week?");

        assertEquals("TOP", ctx.getIntent());
        assertEquals("DEBIT", ctx.getTxnDirection());
        assertEquals("CATEGORY", ctx.getRequestedDimension());
        assertEquals("NONE", ctx.getResolutionMode());
        assertTrue(ctx.isThisWeek());
        assertTrue(ctx.getSearchPhrase() == null || ctx.getSearchPhrase().isBlank());
        assertNull(ctx.getCategoryIds());
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void resolvesMaximumTransactionAmountThisYear() {
        QueryContext ctx = resolverService.resolve(9L, "maximum amount of transaction I made this year");

        assertEquals("MAX", ctx.getIntent());
        assertEquals("NONE", ctx.getRequestedDimension());
        assertEquals("NONE", ctx.getResolutionMode());
        assertTrue(ctx.isYearExplicit());
        assertEquals(Integer.valueOf(java.time.LocalDate.now().getYear()), ctx.getYear());
        assertTrue(ctx.getSearchPhrase() == null || ctx.getSearchPhrase().isBlank());
        assertNull(ctx.getEntityIds());
        assertNull(ctx.getCategoryIds());
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void resolvesLatestTransactionsQueryWithExplicitLimitAndDirection() {
        QueryContext ctx = resolverService.resolve(12L, "show latest 10 debit transactions in april");

        assertEquals("LIST", ctx.getIntent());
        assertEquals(Integer.valueOf(10), ctx.getLimit());
        assertEquals("DEBIT", ctx.getTxnDirection());
        assertEquals(Integer.valueOf(4), ctx.getMonth());
    }

    @Test
    void resolvesThisQuarterToExactDateRange() {
        QueryContext ctx = resolverService.resolve(13L, "transactions this quarter");

        LocalDate now = LocalDate.now();
        LocalDate expectedStart = LocalDate.of(now.getYear(), ((now.getMonthValue() - 1) / 3) * 3 + 1, 1);

        assertEquals("LIST", ctx.getIntent());
        assertEquals(expectedStart, ctx.getFromDate());
        assertEquals(now, ctx.getToDate());
    }

    @Test
    void resolvesExplicitIsoDateRange() {
        QueryContext ctx = resolverService.resolve(14L, "show transactions between 2026-04-27 and 2026-05-07");

        assertEquals("LIST", ctx.getIntent());
        assertEquals(LocalDate.of(2026, 4, 27), ctx.getFromDate());
        assertEquals(LocalDate.of(2026, 5, 7), ctx.getToDate());
        assertTrue(ctx.isYearExplicit());
    }

    @Test
    void keepsMaximumTransactionQuestionDimensionless() {
        QueryContext ctx = resolverService.resolve(10L, "maximum amount of transaction I made this year ?");

        assertEquals("MAX", ctx.getIntent());
        assertEquals("DEBIT", ctx.getTxnDirection());
        assertEquals("NONE", ctx.getRequestedDimension());
        assertEquals("NONE", ctx.getResolutionMode());
        assertTrue(ctx.getSearchPhrase() == null || ctx.getSearchPhrase().isBlank());
        assertNull(ctx.getEntityIds());
        assertNull(ctx.getCategoryIds());
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void resolvesEntityExtremaQuestionWithoutInventingFilter() {
        QueryContext ctx = resolverService.resolve(11L, "To whom I made maximum amount of transaction this year and how much is the amount ?");

        assertEquals("MAX", ctx.getIntent());
        assertEquals("DEBIT", ctx.getTxnDirection());
        assertEquals("ENTITY", ctx.getRequestedDimension());
        assertEquals("NONE", ctx.getResolutionMode());
        assertTrue(ctx.isYearExplicit());
        assertTrue(ctx.getSearchPhrase() == null || ctx.getSearchPhrase().isBlank());
        assertNull(ctx.getEntityIds());
        assertNull(ctx.getCategoryIds());
        Mockito.verifyNoInteractions(llmService);
    }

    @Test
    void resolvesWhereAmISpendingMostAsTopMerchantQuery() {
        QueryContext ctx = resolverService.resolve(16L, "where am I spending most");

        assertEquals("TOP", ctx.getIntent());
        assertEquals("DEBIT", ctx.getTxnDirection());
        assertEquals("ENTITY", ctx.getRequestedDimension());
        assertEquals("NONE", ctx.getResolutionMode());
        assertTrue(ctx.getSearchPhrase() == null || ctx.getSearchPhrase().isBlank());
        assertNull(ctx.getEntityIds());
        assertNull(ctx.getCategoryIds());
    }

    @Test
    void resolvesWhatAmISpendingMostOnAsTopCategoryQuery() {
        QueryContext ctx = resolverService.resolve(17L, "what am I spending most on");

        assertEquals("TOP", ctx.getIntent());
        assertEquals("DEBIT", ctx.getTxnDirection());
        assertEquals("CATEGORY", ctx.getRequestedDimension());
        assertEquals("NONE", ctx.getResolutionMode());
        assertTrue(ctx.getSearchPhrase() == null || ctx.getSearchPhrase().isBlank());
        assertNull(ctx.getEntityIds());
        assertNull(ctx.getCategoryIds());
    }
}
