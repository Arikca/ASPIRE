package com.example.evaluator.service;

import com.example.evaluator.domain.SpecAtom;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class RequirementExtractor {
    public List<SpecAtom> extract(String spec) {
        String text = spec.toLowerCase(Locale.ROOT);
        List<SpecAtom> atoms = new ArrayList<>();
        if (text.contains("fx") && text.contains("rate") && (text.contains("lock") || text.contains("locked"))) {
            atoms.add(new SpecAtom("fx_lock", "FX rate is locked at top-up initiation"));
        }
        if ((text.contains("credited") || text.contains("credit"))
                && (text.contains("debited") || text.contains("debit"))) {
            atoms.add(new SpecAtom("credit_before_debit", "Destination wallet is credited before funding source is debited"));
        }
        if ((text.contains("timeout") || text.contains("times out") || text.contains("timed out"))
                && (text.contains("rollback") || text.contains("rolled back"))) {
            atoms.add(new SpecAtom("timeout_rollback", "Provider callback timeout rolls back the transaction with neither side affected"));
        }
        if (text.contains("approval") && (text.contains("owner") || text.contains("second"))) {
            atoms.add(new SpecAtom("dual_approval", "A top-up above the stated threshold needs second-owner approval before settlement"));
        }
        if (atoms.isEmpty()) {
            atoms.add(new SpecAtom("spec_overall", "Behaviour described by the supplied product specification"));
        }
        return atoms;
    }
}
