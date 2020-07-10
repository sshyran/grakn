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

package grakn.core.graph.edge;

import grakn.core.graph.iid.EdgeIID;
import grakn.core.graph.util.Schema;
import grakn.core.graph.vertex.Vertex;

public interface Edge<
        EDGE_SCHEMA extends Schema.Edge,
        EDGE_IID extends EdgeIID<EDGE_SCHEMA, ?, ?, ?>,
        VERTEX extends Vertex> {

    /**
     * Returns the schema of this edge.
     *
     * @return the schema of this edge
     */
    EDGE_SCHEMA schema();

    /**
     * Returns the {@code iid} of this edge pointing outwards.
     *
     * @return the {@code iid} of this edge pointing outwards
     */
    EDGE_IID outIID();

    /**
     * Returns the {@code iid} of this edge pointing inwards.
     *
     * @return the {@code iid} of this edge pointing inwards
     */
    EDGE_IID inIID();

    /**
     * Returns the tail vertex of this edge.
     *
     * @return the tail vertex of this edge
     */
    VERTEX from();

    /**
     * Returns the head vertex of this edge.
     *
     * @return the head vertex of this edge
     */
    VERTEX to();

    /**
     * Deletes this edge from the graph.
     *
     * The delete operation should also remove this edge from its tail and head vertices.
     */
    void delete();

    /**
     * Commits the edge to the graph for persistent storage.
     */
    void commit();
}
