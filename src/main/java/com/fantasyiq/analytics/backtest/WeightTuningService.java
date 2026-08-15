package com.fantasyiq.analytics.backtest;

import com.fantasyiq.domain.recommendation.Recommendation;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure read/analysis, no mutation -- unlike BacktestService, doesn't
 * regenerate anything, just analyzes whatever START_SIT recommendations
 * already exist for the season (run /generate or /backtest first). Not
 * wrapped in IngestionRunService.track for the same reason
 * GET /recommendations/start-sit isn't: it's a read, not a write whose
 * success/failure needs an audit trail.
 *
 * For each of the five continuous-valued factors, fits a simple linear
 * regression of actual fantasy points against that factor's *current*
 * contribution (see FactorTuningSuggestion for why the resulting slope is
 * directly usable as a weight-rescaling suggestion). INJURY is
 * deliberately excluded -- it's a categorical hard-override, not a
 * continuous scale, and OUT/IR rows are already excluded from the matched
 * set by RecommendationMatcher.
 */
@Service
public class WeightTuningService {

    private static final String TYPE = "START_SIT";
    private static final List<String> TUNABLE_FACTOR_TYPES =
            List.of("MATCHUP", "VEGAS", "WEATHER", "USAGE", "RECENT_PERFORMANCE");

    private final RecommendationRepository recommendationRepository;
    private final RecommendationMatcher recommendationMatcher;

    public WeightTuningService(RecommendationRepository recommendationRepository,
                                RecommendationMatcher recommendationMatcher) {
        this.recommendationRepository = recommendationRepository;
        this.recommendationMatcher = recommendationMatcher;
    }

    public WeightTuningResult analyzeWeights(int season) {
        List<Recommendation> recommendations = recommendationRepository.findBySeasonAndType(season, TYPE);
        RecommendationMatcher.MatchResult matchResult = recommendationMatcher.match(recommendations, season);

        List<FactorTuningSuggestion> suggestions = new ArrayList<>();
        for (String factorType : TUNABLE_FACTOR_TYPES) {
            suggestions.add(analyzeFactor(factorType, matchResult.matched()));
        }

        return new WeightTuningResult(season, matchResult.matched().size(), suggestions);
    }

    private FactorTuningSuggestion analyzeFactor(String factorType, List<MatchedRecommendation> matched) {
        List<Double> contributions = new ArrayList<>();
        List<Double> actuals = new ArrayList<>();

        for (MatchedRecommendation match : matched) {
            match.recommendation().getFactors().stream()
                    .filter(f -> factorType.equals(f.getFactorType()))
                    .findFirst()
                    .ifPresent(f -> {
                        contributions.add(f.getContribution().doubleValue());
                        actuals.add(match.actualPoints());
                    });
        }

        Double correlation = PearsonCorrelation.of(contributions, actuals);
        Double slope = SimpleLinearRegression.of(contributions, actuals)
                .map(SimpleLinearRegression.Fit::slope)
                .orElse(null);

        return new FactorTuningSuggestion(factorType, contributions.size(), correlation, slope);
    }
}
