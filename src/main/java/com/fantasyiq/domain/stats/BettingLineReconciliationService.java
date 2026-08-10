package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.game.Game;
import com.fantasyiq.domain.team.Team;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class BettingLineReconciliationService {

    private final BettingLineRepository bettingLineRepository;

    public BettingLineReconciliationService(BettingLineRepository bettingLineRepository) {
        this.bettingLineRepository = bettingLineRepository;
    }

    @Transactional
    public BettingLine resolveOrCreate(Game game, Team team, BigDecimal impliedTeamTotal,
                                        BigDecimal spread, BigDecimal overUnder, String source) {
        Optional<BettingLine> existing = bettingLineRepository.findByGameAndTeam(game, team);

        if (existing.isPresent()) {
            BettingLine line = existing.get();
            line.updateFrom(impliedTeamTotal, spread, overUnder, source);
            return line;
        }

        return bettingLineRepository.save(
                new BettingLine(game, team, impliedTeamTotal, spread, overUnder, source));
    }
}
