package com.fantasyiq.ingestion.scheduler;

import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.game.GameRepository;
import com.fantasyiq.domain.stats.BettingLineReconciliationService;
import com.fantasyiq.domain.team.Team;
import com.fantasyiq.domain.team.TeamRepository;
import com.fantasyiq.ingestion.odds.OddsProvider;
import com.fantasyiq.ingestion.odds.RawGameOdds;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The Odds API has no concept of "our" season/week or game ids -- it just
 * returns whatever's currently on the board, identified by team name and
 * kickoff time. Each returned game is matched against our games table by
 * (home team, away team), picking whichever candidate's kickoff is closest
 * to the odds response's commence_time, within a tolerance -- division
 * rivals can play twice in a season, so team pair alone isn't always
 * unique. Unmatched games (unknown team name, no game within tolerance)
 * are skipped, not an error, same as unresolved injury reports.
 */
@Service
public class OddsIngestionService {

    private static final String SOURCE = "ODDS_PROVIDER";
    private static final Duration MATCH_TOLERANCE = Duration.ofDays(3);

    private final OddsProvider oddsProvider;
    private final TeamRepository teamRepository;
    private final GameRepository gameRepository;
    private final BettingLineReconciliationService bettingLineReconciliationService;
    private final IngestionRunService ingestionRunService;

    public OddsIngestionService(OddsProvider oddsProvider, TeamRepository teamRepository,
                                 GameRepository gameRepository,
                                 BettingLineReconciliationService bettingLineReconciliationService,
                                 IngestionRunService ingestionRunService) {
        this.oddsProvider = oddsProvider;
        this.teamRepository = teamRepository;
        this.gameRepository = gameRepository;
        this.bettingLineReconciliationService = bettingLineReconciliationService;
        this.ingestionRunService = ingestionRunService;
    }

    public int ingestOdds() {
        return ingestionRunService.track(SOURCE, Integer::intValue, this::doIngestOdds);
    }

    private int doIngestOdds() {
        int bettingLinesIngested = 0;
        for (RawGameOdds odds : oddsProvider.fetchCurrentOdds()) {
            Optional<Team> homeTeam = teamRepository.findByName(odds.homeTeamName());
            Optional<Team> awayTeam = teamRepository.findByName(odds.awayTeamName());
            if (homeTeam.isEmpty() || awayTeam.isEmpty()) {
                continue;
            }

            Optional<Game> game = resolveGame(homeTeam.get(), awayTeam.get(), odds);
            if (game.isEmpty()) {
                continue;
            }

            BigDecimal homeImplied = impliedTeamTotal(odds.overUnder(), odds.homeSpread());
            BigDecimal awayImplied = impliedTeamTotal(odds.overUnder(), odds.awaySpread());

            bettingLineReconciliationService.resolveOrCreate(game.get(), homeTeam.get(), homeImplied,
                    odds.homeSpread(), odds.overUnder(), odds.source());
            bettingLineReconciliationService.resolveOrCreate(game.get(), awayTeam.get(), awayImplied,
                    odds.awaySpread(), odds.overUnder(), odds.source());
            bettingLinesIngested += 2;
        }
        return bettingLinesIngested;
    }

    private Optional<Game> resolveGame(Team homeTeam, Team awayTeam, RawGameOdds odds) {
        List<Game> candidates = gameRepository.findByHomeTeamAndAwayTeam(homeTeam, awayTeam);
        return candidates.stream()
                .filter(g -> Duration.between(g.getKickoff(), odds.commenceTime()).abs().compareTo(MATCH_TOLERANCE) <= 0)
                .min(Comparator.comparing(g -> Duration.between(g.getKickoff(), odds.commenceTime()).abs()));
    }

    private BigDecimal impliedTeamTotal(BigDecimal overUnder, BigDecimal teamSpread) {
        return overUnder.subtract(teamSpread).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
}
