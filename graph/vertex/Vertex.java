/*
 * Copyright (C) 2020 Grakn Labs
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

package grakn.core.graph.vertex;

import grakn.core.graph.Graph;
import grakn.core.graph.adjacency.Adjacency;
import grakn.core.graph.edge.Edge;
import grakn.core.graph.iid.VertexIID;
import grakn.core.graph.util.Schema;

public interface Vertex<
        VERTEX_IID extends VertexIID,
        VERTEX_SCHEMA extends Schema.Vertex,
        VERTEX extends Vertex<VERTEX_IID, VERTEX_SCHEMA, VERTEX, EDGE_SCHEMA, EDGE>,
        EDGE_SCHEMA extends Schema.Edge,
        EDGE extends Edge<?, ?, VERTEX>> {

    /**
     * Returns the {@code Graph} containing all the {@code Vertex}.
     *
     * @return the {@code Graph} containing all the {@code Vertex}
     */
    Graph<VERTEX_IID, VERTEX> graph();

    VERTEX_IID iid();

    void iid(VERTEX_IID iid);

    Schema.Status status();

    VERTEX_SCHEMA schema();

    Adjacency<EDGE_SCHEMA, EDGE, VERTEX> outs();

    Adjacency<EDGE_SCHEMA, EDGE, VERTEX> ins();

    void setModified();

    boolean isModified();

    void delete();

    boolean isDeleted();

    /**
     * Commits this {@code ThingVertex} to be persisted onto storage.
     */
    void commit();
}
