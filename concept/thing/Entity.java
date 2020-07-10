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

package grakn.core.concept.thing;

import grakn.core.concept.type.EntityType;

public interface Entity extends Thing {

    /**
     * Cast the {@code Concept} down to {@code Entity}
     *
     * @return this {@code Entity}
     */
    @Override
    default Entity asEntity() { return this; }

    /**
     * Get the immediate {@code EntityType} in which this this {@code Entity} is an instance of.
     *
     * @return the {@code EntityType} of this {@code Entity}
     */
    @Override
    EntityType type();

    /**
     * Set an {@code Attribute} to be owned by this {@code Entity}.
     *
     * @param attribute that will be owned by this {@code Entity}
     * @return this {@code Entity} for further manipulation
     */
    @Override
    Entity has(Attribute attribute);


}
