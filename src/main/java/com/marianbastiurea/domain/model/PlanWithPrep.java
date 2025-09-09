package com.marianbastiurea.domain.model;

import com.marianbastiurea.domain.enums.JarType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PlanWithPrep {

    private static final Logger log = LoggerFactory.getLogger(PlanWithPrep.class);

    private final AllocationPlan plan;
    private final Map<JarType, Integer> fulfilledJarQty;
    private final BigDecimal honeyKgUsed;
    private final List<PrepCommand> prepCommands;

    public PlanWithPrep(AllocationPlan plan,
                        Map<JarType, Integer> fulfilledJarQty,
                        BigDecimal honeyKgUsed,
                        List<PrepCommand> prepCommands) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.fulfilledJarQty = Map.copyOf(Objects.requireNonNull(fulfilledJarQty, "fulfilledJarQty"));
        this.honeyKgUsed = Objects.requireNonNull(honeyKgUsed, "honeyKgUsed");
        this.prepCommands = List.copyOf(Objects.requireNonNull(prepCommands, "prepCommands"));

        // log de creare — concis, pe DEBUG
        int jarTypes = this.fulfilledJarQty.size();
        int totalJars = this.fulfilledJarQty.values().stream().mapToInt(Integer::intValue).sum();
        int cmds = this.prepCommands.size();
        log.debug("Created PlanWithPrep: planClass={}, jarTypes={}, totalJars={}, honeyKgUsed={}, prepCommands={}",
                this.plan.getClass().getSimpleName(), jarTypes, totalJars, this.honeyKgUsed, cmds);
    }

    public AllocationPlan getPlan() {
        return plan;
    }

    public Map<JarType, Integer> getFulfilledJarQty() {
        return fulfilledJarQty;
    }

    public BigDecimal getHoneyKgUsed() {
        return honeyKgUsed;
    }

    public List<PrepCommand> getPrepCommands() {
        return prepCommands;
    }

    /**
     * Rezumat util pentru loguri (fără a lista toate colecțiile).
     */
    public String summary() {
        int jarTypes = fulfilledJarQty.size();
        int totalJars = fulfilledJarQty.values().stream().mapToInt(Integer::intValue).sum();
        return "PlanWithPrepSummary{planClass=" + plan.getClass().getSimpleName() +
                ", jarTypes=" + jarTypes +
                ", totalJars=" + totalJars +
                ", honeyKgUsed=" + honeyKgUsed +
                ", prepCommands=" + prepCommands.size() +
                '}';
    }

    @Override
    public String toString() {
        return "PlanWithPrep{" +
                "plan=" + plan +
                ", fulfilledJarQty=" + fulfilledJarQty +
                ", honeyKgUsed=" + honeyKgUsed +
                ", prepCommands=" + prepCommands +
                '}';
    }
}
