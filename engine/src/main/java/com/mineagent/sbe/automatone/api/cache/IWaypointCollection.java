// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/api/cache/IWaypointCollection.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mineagent.sbe.automatone.api.cache;

import java.util.Set;

/**
 * @author Brady
 * @since 9/24/2018
 */
public interface IWaypointCollection {

    /**
     * Adds a waypoint to this collection
     *
     * @param waypoint The waypoint
     */
    void addWaypoint(IWaypoint waypoint);

    /**
     * Removes a waypoint from this collection
     *
     * @param waypoint The waypoint
     */
    void removeWaypoint(IWaypoint waypoint);

    /**
     * Gets the most recently created waypoint by the specified {@link IWaypoint.Tag}
     *
     * @param tag The tag
     * @return The most recently created waypoint with the specified tag
     */
    IWaypoint getMostRecentByTag(IWaypoint.Tag tag);

    /**
     * Gets all of the waypoints that have the specified tag
     *
     * @param tag The tag
     * @return All of the waypoints with the specified tag
     * @see IWaypointCollection#getAllWaypoints()
     */
    Set<IWaypoint> getByTag(IWaypoint.Tag tag);

    /**
     * Gets all of the waypoints in this collection, regardless of the tag.
     *
     * @return All of the waypoints in this collection
     * @see IWaypointCollection#getByTag(IWaypoint.Tag)
     */
    Set<IWaypoint> getAllWaypoints();
}
