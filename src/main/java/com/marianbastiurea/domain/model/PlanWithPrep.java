package com.marianbastiurea.domain.model;

import com.marianbastiurea.domain.enums.JarType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class PlanWithPrep {
    private final AllocationPlan plan;
    private final Map<JarType, Integer> fulfilledJarQty;
    private final BigDecimal honeyKgUsed;
    private final List<PrepCommand> prepCommands;

    public PlanWithPrep(AllocationPlan plan,
                        Map<JarType, Integer> fulfilledJarQty,
                        BigDecimal honeyKgUsed,
                        List<PrepCommand> prepCommands) {
        this.plan = plan;
        this.fulfilledJarQty = fulfilledJarQty;
        this.honeyKgUsed = honeyKgUsed;
        this.prepCommands = prepCommands;
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
