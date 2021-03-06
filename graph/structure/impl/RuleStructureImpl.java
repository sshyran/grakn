/*
 * Copyright (C) 2021 Grakn Labs
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

package grakn.core.graph.structure.impl;

import grakn.core.common.iterator.ResourceIterator;
import grakn.core.common.parameters.Label;
import grakn.core.graph.SchemaGraph;
import grakn.core.graph.common.Encoding;
import grakn.core.graph.iid.IndexIID;
import grakn.core.graph.iid.StructureIID;
import grakn.core.graph.structure.RuleStructure;
import grakn.core.graph.vertex.TypeVertex;
import graql.lang.Graql;
import graql.lang.pattern.Conjunctable;
import graql.lang.pattern.Conjunction;
import graql.lang.pattern.Negation;
import graql.lang.pattern.Pattern;
import graql.lang.pattern.constraint.TypeConstraint;
import graql.lang.pattern.variable.BoundVariable;
import graql.lang.pattern.variable.ThingVariable;
import graql.lang.pattern.variable.Variable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static grakn.core.common.collection.Bytes.join;
import static grakn.core.common.iterator.Iterators.iterate;
import static grakn.core.common.iterator.Iterators.link;
import static grakn.core.graph.common.Encoding.Property.LABEL;
import static grakn.core.graph.common.Encoding.Property.THEN;
import static grakn.core.graph.common.Encoding.Property.WHEN;

public abstract class RuleStructureImpl implements RuleStructure {

    final SchemaGraph graph;
    final AtomicBoolean isDeleted;
    final Conjunction<? extends Pattern> when;
    final ThingVariable<?> then;
    StructureIID.Rule iid;
    String label;

    private boolean isModified;

    RuleStructureImpl(SchemaGraph graph, StructureIID.Rule iid, String label,
                      Conjunction<? extends Pattern> when, ThingVariable<?> then) {
        assert when != null;
        assert then != null;
        this.graph = graph;
        this.iid = iid;
        this.label = label;
        this.when = when;
        this.then = then;
        this.isDeleted = new AtomicBoolean(false);
    }

    @Override
    public StructureIID.Rule iid() {
        return iid;
    }

    @Override
    public void iid(StructureIID.Rule iid) {
        this.iid = iid;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public boolean isModified() {
        return isModified;
    }

    @Override
    public void setModified() {
        if (!isModified) {
            isModified = true;
            graph.setModified();
        }
    }

    @Override
    public boolean isDeleted() {
        return isDeleted.get();
    }

    @Override
    public void indexConcludesVertex(Label type) {
        graph.rules().conclusions().buffered().concludesVertex(this, graph.getType(type));
    }

    @Override
    public void unindexConcludesVertex(Label type) {
        graph.rules().conclusions().deleteConcludesVertex(this, graph.getType(type));
    }

    @Override
    public void indexConcludesEdgeTo(Label type) {
        graph.rules().conclusions().buffered().concludesEdgeTo(this, graph.getType(type));
    }

    @Override
    public void unindexConcludesEdgeTo(Label type) {
        graph.rules().conclusions().deleteConcludesEdgeTo(this, graph.getType(type));
    }

    public Encoding.Structure encoding() {
        return iid.encoding();
    }

    void deleteVertexFromGraph() {
        graph.rules().delete(this);
    }

    ResourceIterator<TypeVertex> types() {
        graql.lang.pattern.Conjunction<Conjunctable> whenNormalised = when().normalise().patterns().get(0);
        ResourceIterator<BoundVariable> positiveVariables = iterate(whenNormalised.patterns()).filter(Conjunctable::isVariable)
                .map(Conjunctable::asVariable);
        ResourceIterator<BoundVariable> negativeVariables = iterate(whenNormalised.patterns()).filter(Conjunctable::isNegation)
                .flatMap(p -> negationVariables(p.asNegation()));
        ResourceIterator<Label> whenPositiveLabels = getTypeLabels(positiveVariables);
        ResourceIterator<Label> whenNegativeLabels = getTypeLabels(negativeVariables);
        ResourceIterator<Label> thenLabels = getTypeLabels(iterate(then().variables().iterator()));
        // filter out invalid labels as if they were truly invalid (eg. not relation:friend) we will catch it validation
        // this lets us index only types the user can actually retrieve as a concept
        return link(whenPositiveLabels, whenNegativeLabels, thenLabels)
                .filter(label -> graph.getType(label) != null).map(graph::getType);
    }

    private ResourceIterator<BoundVariable> negationVariables(Negation<?> ruleNegation) {
        assert ruleNegation.patterns().size() == 1 && ruleNegation.patterns().get(0).isDisjunction();
        return iterate(ruleNegation.patterns().get(0).asDisjunction().patterns())
                .flatMap(pattern -> iterate(pattern.asConjunction().patterns())).map(Pattern::asVariable);
    }

    private ResourceIterator<Label> getTypeLabels(ResourceIterator<BoundVariable> variables) {
        return variables.flatMap(v -> iterate(connectedVars(v, new HashSet<>())))
                .distinct().filter(v -> v.isBound() && v.asBound().isType()).map(var -> var.asBound().asType().label()).filter(Optional::isPresent)
                .map(labelConstraint -> {
                    TypeConstraint.Label label = labelConstraint.get();
                    if (label.scope().isPresent()) return Label.of(label.label(), label.scope().get());
                    else return Label.of(label.label());
                });
    }

    private Set<Variable> connectedVars(Variable var, Set<Variable> visited) {
        visited.add(var);
        Set<Variable> vars = iterate(var.constraints()).flatMap(c -> iterate(c.variables())).map(v -> (Variable) v).toSet();
        if (visited.containsAll(vars)) return visited;
        else {
            visited.addAll(vars);
            return iterate(vars).flatMap(v -> iterate(connectedVars(v, visited))).toSet();
        }
    }

    public static class Buffered extends RuleStructureImpl {

        private final AtomicBoolean isCommitted;

        public Buffered(SchemaGraph graph, StructureIID.Rule iid, String label, Conjunction<? extends Pattern> when, ThingVariable<?> then) {
            super(graph, iid, label, when, then);
            this.isCommitted = new AtomicBoolean(false);
            setModified();
            indexReferences();
        }

        @Override
        public void label(String label) {
            graph.rules().update(this, this.label, label);
            this.label = label;
        }

        @Override
        public Encoding.Status status() {
            return isCommitted.get() ? Encoding.Status.COMMITTED : Encoding.Status.BUFFERED;
        }

        @Override
        public Conjunction<? extends Pattern> when() { return when; }

        @Override
        public ThingVariable<?> then() { return then; }

        @Override
        public void delete() {
            if (isDeleted.compareAndSet(false, true)) {
                graph.rules().references().delete(this, types());
                deleteVertexFromGraph();
            }
        }

        @Override
        public void commit() {
            if (isCommitted.compareAndSet(false, true)) {
                commitVertex();
                commitProperties();
            }
        }

        private void commitVertex() {
            graph.storage().put(iid.bytes());
            graph.storage().put(IndexIID.Rule.of(label).bytes(), iid.bytes());
        }

        private void commitProperties() {
            commitPropertyLabel();
            commitWhen();
            commitThen();
        }

        private void commitPropertyLabel() {
            graph.storage().put(join(iid.bytes(), LABEL.infix().bytes()), label.getBytes());
        }

        private void commitWhen() {
            graph.storage().put(join(iid.bytes(), WHEN.infix().bytes()), when().toString().getBytes());
        }

        private void commitThen() {
            graph.storage().put(join(iid.bytes(), THEN.infix().bytes()), then().toString().getBytes());
        }

        private void indexReferences() {
            types().forEachRemaining(type -> graph.rules().references().buffered().put(this, type));
        }

    }

    public static class Persisted extends RuleStructureImpl {

        public Persisted(SchemaGraph graph, StructureIID.Rule iid) {
            super(graph, iid,
                  new String(graph.storage().get(join(iid.bytes(), LABEL.infix().bytes()))),
                  Graql.parsePattern(new String(graph.storage().get(join(iid.bytes(), WHEN.infix().bytes())))).asConjunction(),
                  Graql.parseVariable(new String(graph.storage().get(join(iid.bytes(), THEN.infix().bytes())))).asThing());
        }

        @Override
        public Encoding.Status status() {
            return Encoding.Status.PERSISTED;
        }

        @Override
        public Conjunction<? extends Pattern> when() {
            return when;
        }

        @Override
        public ThingVariable<?> then() {
            return then;
        }

        @Override
        public void label(String label) {
            graph.rules().update(this, this.label, label);
            graph.storage().put(join(iid.bytes(), LABEL.infix().bytes()), label.getBytes());
            graph.storage().delete(IndexIID.Rule.of(this.label).bytes());
            graph.storage().put(IndexIID.Rule.of(label).bytes(), iid.bytes());
            this.label = label;
        }

        @Override
        public void delete() {
            if (isDeleted.compareAndSet(false, true)) {
                graph.rules().references().delete(this, types());
                deleteVertexFromGraph();
                deleteVertexFromStorage();
            }
        }

        private void deleteVertexFromStorage() {
            graph.storage().delete(IndexIID.Rule.of(label).bytes());
            ResourceIterator<byte[]> keys = graph.storage().iterate(iid.bytes(), (iid, value) -> iid);
            while (keys.hasNext()) graph.storage().delete(keys.next());
        }

        @Override
        public void commit() {}
    }

}
