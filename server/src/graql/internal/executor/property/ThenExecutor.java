/*
 * GRAKN.AI - THE KNOWLEDGE GRAPH
 * Copyright (C) 2018 Grakn Labs Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package grakn.core.graql.internal.executor.property;

import com.google.common.collect.ImmutableSet;
import grakn.core.graql.internal.reasoner.atom.Atomic;
import grakn.core.graql.internal.reasoner.query.ReasonerQuery;
import grakn.core.graql.exception.GraqlQueryException;
import grakn.core.graql.internal.executor.WriteExecutor;
import grakn.core.graql.internal.gremlin.EquivalentFragmentSet;
import grakn.core.graql.query.pattern.property.ThenProperty;
import grakn.core.graql.query.pattern.property.VarProperty;
import grakn.core.graql.query.pattern.statement.Statement;
import grakn.core.graql.query.pattern.statement.Variable;

import java.util.Set;

public class ThenExecutor implements PropertyExecutor.Definable {

    private final Variable var;
    private final ThenProperty property;

    ThenExecutor(Variable var, ThenProperty property) {
        this.var = var;
        this.property = property;
    }

    @Override
    public Set<EquivalentFragmentSet> matchFragments() {
        return null;
    }

    @Override
    public Atomic atomic(ReasonerQuery parent, Statement statement, Set<Statement> otherStatements) {
        return null;
    }

    @Override
    public Set<PropertyExecutor.Writer> defineExecutors() {
        return ImmutableSet.of(new DefineThen());
    }

    @Override
    public Set<PropertyExecutor.Writer> undefineExecutors() {
        // TODO: Fix this so that Undefining ThenProperty behaves symmetrically to Defining ThenProperty
        throw GraqlQueryException.defineUnsupportedProperty(property.keyword());
    }

    private class DefineThen implements PropertyExecutor.Writer {

        @Override
        public Variable var() {
            return var;
        }

        @Override
        public VarProperty property() {
            return property;
        }

        @Override
        public Set<Variable> requiredVars() {
            return ImmutableSet.of();
        }

        @Override
        public Set<Variable> producedVars() {
            return ImmutableSet.of(var);
        }

        @Override
        public void execute(WriteExecutor executor) {
            // This allows users to skip stating `$ruleVar sub rule` when they say `$ruleVar then { ... }`
            executor.getBuilder(var).isRule().then(property.pattern());
        }
    }
}