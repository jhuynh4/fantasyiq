package com.fantasyiq.analytics.scoring;

import java.math.BigDecimal;

/**
 * One calculator's output -- maps directly onto a recommendation_factors
 * row. contribution is what actually gets summed into the composite score;
 * factorValue/factorWeight are kept alongside it so the breakdown stays
 * inspectable (contribution = factorValue-derived, not necessarily a literal
 * factorValue * factorWeight product for every calculator, but always
 * traceable back to them via the narrative).
 */
public record FactorResult(String factorType, BigDecimal factorValue, BigDecimal factorWeight,
                            BigDecimal contribution, String narrative) {
}
