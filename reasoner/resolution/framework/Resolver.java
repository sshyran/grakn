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
 *
 */

package grakn.core.reasoner.resolution.framework;

import grakn.core.common.exception.GraknException;
import grakn.core.common.iterator.Iterators;
import grakn.core.common.iterator.ResourceIterator;
import grakn.core.concept.Concept;
import grakn.core.concept.ConceptManager;
import grakn.core.concept.answer.ConceptMap;
import grakn.core.concurrent.actor.Actor;
import grakn.core.pattern.Conjunction;
import grakn.core.pattern.variable.Variable;
import grakn.core.reasoner.resolution.ResolverRegistry;
import grakn.core.traversal.Traversal;
import grakn.core.traversal.TraversalEngine;
import grakn.core.traversal.common.Identifier;
import graql.lang.pattern.variable.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static grakn.core.common.exception.ErrorMessage.Internal.ILLEGAL_STATE;

public abstract class Resolver<T extends Resolver<T>> extends Actor.State<T> {
    private static final Logger LOG = LoggerFactory.getLogger(Resolver.class);

    private final String name;
    private final Map<Request, Request> requestRouter;
    protected final ResolverRegistry registry;
    protected final TraversalEngine traversalEngine;
    private final boolean explanations;

    protected Resolver(Actor<T> self, String name, ResolverRegistry registry, TraversalEngine traversalEngine, boolean explanations) {
        super(self);
        this.name = name;
        this.registry = registry;
        this.traversalEngine = traversalEngine;
        this.explanations = explanations;
        this.requestRouter = new HashMap<>();
        // Note: initialising downstream actors in constructor will create all actors ahead of time, so it is non-lazy
        // additionally, it can cause deadlock within ResolverRegistry as different threads initialise actors
    }

    public String name() {
        return name;
    }

    protected boolean explanations() { return explanations; }

    public abstract void receiveRequest(Request fromUpstream, int iteration);

    protected abstract void receiveAnswer(Response.Answer fromDownstream, int iteration);

    protected abstract void receiveExhausted(Response.Fail fromDownstream, int iteration);

    protected abstract void initialiseDownstreamActors();

    protected abstract ResponseProducer responseProducerCreate(Request fromUpstream, int iteration);

    protected abstract ResponseProducer responseProducerReiterate(Request fromUpstream, ResponseProducer responseProducer, int newIteration);

    protected Request fromUpstream(Request toDownstream) {
        assert requestRouter.containsKey(toDownstream);
        return requestRouter.get(toDownstream);
    }

    protected void requestFromDownstream(Request request, Request fromUpstream, int iteration) {
        LOG.trace("{} : Sending a new answer Request to downstream: {}", name, request);
        // TODO: we may overwrite if multiple identical requests are sent, when to clean up?
        requestRouter.put(request, fromUpstream);
        Actor<? extends Resolver<?>> receiver = request.receiver();
        receiver.tell(actor -> actor.receiveRequest(request, iteration));
    }

    protected void respondToUpstream(Response response, int iteration) {
        Actor<? extends Resolver<?>> receiver = response.sourceRequest().sender();
        if (response.isAnswer()) {
            LOG.trace("{} : Sending a new Response.Answer to upstream", name());
            receiver.tell(actor -> actor.receiveAnswer(response.asAnswer(), iteration));
        } else if (response.isFail()) {
            LOG.trace("{}: Sending a new Response.Fail to upstream", name());
            receiver.tell(actor -> actor.receiveExhausted(response.asFail(), iteration));
        } else {
            throw new RuntimeException(("Unknown response type " + response.getClass().getSimpleName()));
        }
    }

    protected ResourceIterator<ConceptMap> compatibleBoundAnswers(ConceptManager conceptMgr, Conjunction conjunction, ConceptMap bounds) {
        return compatibleBounds(conjunction, bounds).map(b -> {
            Traversal traversal = boundTraversal(conjunction.traversal(), b);
            return traversalEngine.iterator(traversal).map(conceptMgr::conceptMap);
        }).orElse(Iterators.empty());
    }

    private Optional<ConceptMap> compatibleBounds(Conjunction conjunction, ConceptMap bounds) {
        Map<Reference.Name, Concept> newBounds = new HashMap<>();
        for (Map.Entry<Reference.Name, ? extends Concept> entry : bounds.concepts().entrySet()) {
            Reference.Name ref = entry.getKey();
            Concept bound = entry.getValue();
            Variable conjVariable = Iterators.iterate(conjunction.variables()).filter(var -> var.reference().equals(ref)).firstOrNull();
            assert conjVariable != null;
            if (conjVariable.isThing()) {
                if (!conjVariable.asThing().iid().isPresent()) newBounds.put(ref, bound);
                else {
                    if (!Arrays.equals(conjVariable.asThing().iid().get().iid(), bound.asThing().getIID()))
                        return Optional.empty();
                }
            } else if (conjVariable.isType()) {
                if (!conjVariable.asType().label().isPresent()) newBounds.put(ref, bound);
                else {
                    if (!conjVariable.asType().label().get().properLabel().equals(bound.asType().getLabel()))
                        return Optional.empty();
                }
            } else {
                throw GraknException.of(ILLEGAL_STATE);
            }
        }
        return Optional.of(new ConceptMap(newBounds));
    }

    protected Traversal boundTraversal(Traversal traversal, ConceptMap bounds) {
        bounds.concepts().forEach((ref, concept) -> {
            Identifier.Variable.Name id = Identifier.Variable.of(ref);
            if (concept.isThing()) traversal.iid(id, concept.asThing().getIID());
            else {
                traversal.clearLabels(id);
                traversal.labels(id, concept.asType().getLabel());
            }
        });
        return traversal;
    }
}
