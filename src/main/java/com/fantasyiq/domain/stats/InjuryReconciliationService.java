package com.fantasyiq.domain.stats;

import com.fantasyiq.domain.player.Player;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class InjuryReconciliationService {

    private static final String ESPN_SOURCE = "ESPN";

    private final InjuryReportRepository injuryReportRepository;

    public InjuryReconciliationService(InjuryReportRepository injuryReportRepository) {
        this.injuryReportRepository = injuryReportRepository;
    }

    @Transactional
    public InjuryReport resolveOrCreateFromEspn(Player player, String status, LocalDate reportDate) {
        Optional<InjuryReport> existing = injuryReportRepository
                .findByPlayerAndReportDateAndSource(player, reportDate, ESPN_SOURCE);

        if (existing.isPresent()) {
            InjuryReport report = existing.get();
            report.updateStatus(status);
            return report;
        }

        return injuryReportRepository.save(new InjuryReport(player, reportDate, status, ESPN_SOURCE));
    }
}
