package com.fantasyiq.analytics.startsit;

import com.fantasyiq.analytics.scoring.FactorResult;
import com.fantasyiq.analytics.scoring.InjuryFactorCalculator;
import com.fantasyiq.analytics.scoring.MatchupFactorCalculator;
import com.fantasyiq.analytics.scoring.RecentPerformanceFactorCalculator;
import com.fantasyiq.analytics.scoring.UsageTrendFactorCalculator;
import com.fantasyiq.analytics.scoring.VegasImpliedTotalFactorCalculator;
import com.fantasyiq.analytics.scoring.WeatherFactorCalculator;
import com.fantasyiq.cache.RecommendationCacheService;
import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.player.Player;
import com.fantasyiq.domain.player.PlayerRepository;
import com.fantasyiq.domain.recommendation.Recommendation;
import com.fantasyiq.domain.recommendation.RecommendationFactor;
import com.fantasyiq.domain.recommendation.RecommendationReconciliationService;
import com.fantasyiq.domain.recommendation.RecommendationRepository;
import com.fantasyiq.domain.stats.BettingLine;
import com.fantasyiq.domain.stats.BettingLineRepository;
import com.fantasyiq.domain.stats.DefenseVsPositionStats;
import com.fantasyiq.domain.stats.DefenseVsPositionStatsRepository;
import com.fantasyiq.domain.stats.InjuryReport;
import com.fantasyiq.domain.stats.InjuryReportRepository;
import com.fantasyiq.domain.stats.PlayerGameStats;
import com.fantasyiq.domain.stats.PlayerGameStatsRepository;
import com.fantasyiq.domain.stats.WeatherForecast;
import com.fantasyiq.domain.stats.WeatherForecastRepository;
import com.fantasyiq.domain.team.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Composes the shared factor calculators (analytics.scoring) into a single
 * START_SIT recommendation per player, for every QB/RB/WR/TE on either side
 * of every game in the target week. Each factor is independently optional
 * (a calculator returns Optional.empty() when it lacks the data it needs);
 * the composite score is just the sum of whatever contributions are
 * available, and a player with zero available factors gets no recommendation
 * row at all rather than a hollow zero-factor one.
 */
@Service
public class StartSitRecommendationService {

    static final String TYPE = "START_SIT";
    static final String SCORING_VERSION = "v1";

    private static final List<String> SUPPORTED_POSITIONS = List.of("QB", "RB", "WR", "TE");

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final DefenseVsPositionStatsRepository defenseVsPositionStatsRepository;
    private final BettingLineRepository bettingLineRepository;
    private final WeatherForecastRepository weatherForecastRepository;
    private final InjuryReportRepository injuryReportRepository;
    private final PlayerGameStatsRepository playerGameStatsRepository;
    private final RecommendationReconciliationService recommendationReconciliationService;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationCacheService recommendationCacheService;

    public StartSitRecommendationService(GameRepository gameRepository, PlayerRepository playerRepository,
                                          DefenseVsPositionStatsRepository defenseVsPositionStatsRepository,
                                          BettingLineRepository bettingLineRepository,
                                          WeatherForecastRepository weatherForecastRepository,
                                          InjuryReportRepository injuryReportRepository,
                                          PlayerGameStatsRepository playerGameStatsRepository,
                                          RecommendationReconciliationService recommendationReconciliationService,
                                          RecommendationRepository recommendationRepository,
                                          RecommendationCacheService recommendationCacheService) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.defenseVsPositionStatsRepository = defenseVsPositionStatsRepository;
        this.bettingLineRepository = bettingLineRepository;
        this.weatherForecastRepository = weatherForecastRepository;
        this.injuryReportRepository = injuryReportRepository;
        this.playerGameStatsRepository = playerGameStatsRepository;
        this.recommendationReconciliationService = recommendationReconciliationService;
        this.recommendationRepository = recommendationRepository;
        this.recommendationCacheService = recommendationCacheService;
    }

    @Transactional
    public int computeForWeek(int season, int week) {
        int recommendationsGenerated = 0;
        for (Game game : gameRepository.findBySeasonAndWeek(season, week)) {
            recommendationsGenerated += scoreTeam(game, game.getHomeTeam(), game.getAwayTeam(), season, week);
            recommendationsGenerated += scoreTeam(game, game.getAwayTeam(), game.getHomeTeam(), season, week);
        }
        recommendationCacheService.refreshStartSit(season, week);
        return recommendationsGenerated;
    }

    private int scoreTeam(Game game, Team offenseTeam, Team defenseTeam, int season, int week) {
        int recommendationsGenerated = 0;
        for (Player player : playerRepository.findByCurrentTeamAndPositionIn(offenseTeam, SUPPORTED_POSITIONS)) {
            List<FactorResult> factors = gatherFactors(game, offenseTeam, defenseTeam, player, season, week);
            if (factors.isEmpty()) {
                continue;
            }

            BigDecimal score = factors.stream().map(FactorResult::contribution).reduce(BigDecimal.ZERO, BigDecimal::add);
            String confidence = determineConfidence(factors);

            Recommendation recommendation = recommendationReconciliationService.resolveOrCreate(
                    player, season, week, TYPE, score, confidence, SCORING_VERSION);

            List<RecommendationFactor> factorEntities = factors.stream()
                    .map(fr -> new RecommendationFactor(recommendation, fr.factorType(), fr.factorValue(),
                            fr.factorWeight(), fr.contribution(), fr.narrative()))
                    .toList();
            recommendation.replaceFactors(factorEntities);
            recommendationRepository.save(recommendation);

            recommendationsGenerated++;
        }
        return recommendationsGenerated;
    }

    private List<FactorResult> gatherFactors(Game game, Team offenseTeam, Team defenseTeam, Player player,
                                              int season, int week) {
        List<FactorResult> factors = new ArrayList<>();
        String position = player.getPosition();

        List<DefenseVsPositionStats> priorDefense = defenseVsPositionStatsRepository
                .findByTeamAndSeasonAndPositionAndWeekLessThanOrderByWeekAsc(defenseTeam, season, position, week);
        MatchupFactorCalculator.calculate(priorDefense, position).ifPresent(factors::add);

        Optional<BettingLine> line = bettingLineRepository.findByGameAndTeam(game, offenseTeam);
        VegasImpliedTotalFactorCalculator.calculate(line.map(BettingLine::getImpliedTeamTotal).orElse(null))
                .ifPresent(factors::add);

        Optional<WeatherForecast> forecast = weatherForecastRepository.findByGame(game);
        WeatherFactorCalculator.calculate(forecast.orElse(null), position).ifPresent(factors::add);

        Optional<InjuryReport> injury = injuryReportRepository.findTopByPlayerOrderByReportDateDesc(player);
        InjuryFactorCalculator.calculate(injury.map(InjuryReport::getStatus).orElse(null)).ifPresent(factors::add);

        List<PlayerGameStats> priorGames = playerGameStatsRepository
                .findByPlayerAndGame_SeasonAndGame_WeekLessThanOrderByGame_WeekDesc(player, season, week);
        UsageTrendFactorCalculator.calculate(priorGames, position).ifPresent(factors::add);
        RecentPerformanceFactorCalculator.calculate(priorGames).ifPresent(factors::add);

        return factors;
    }

    private String determineConfidence(List<FactorResult> factors) {
        boolean hasInjuryOverride = factors.stream()
                .anyMatch(f -> "INJURY".equals(f.factorType()) && f.contribution().compareTo(BigDecimal.valueOf(-100)) < 0);
        if (hasInjuryOverride) {
            return "LOW";
        }
        if (factors.size() >= 4) {
            return "HIGH";
        }
        if (factors.size() >= 2) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
