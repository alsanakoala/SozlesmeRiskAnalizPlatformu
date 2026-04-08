package com.riskguard.rules;

import java.util.Iterator;
import java.util.List;

public class RulePack implements Iterable<RuleDef> {
    public List<RuleDef> rules;

    @Override
    public Iterator<RuleDef> iterator() {
        return rules.iterator();
    }
}
