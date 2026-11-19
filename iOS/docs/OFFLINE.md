# Regional offline routing

Settings > Offline regions imports OSM XML (.osm/.xml) or RoadPack JSON version 1.
HTTPS downloads require expected SHA-256. Activate explicitly; Nav owns the router.
GPX following is separate. Sources/Core/OfflineGraph.swift defines the schema:
format navisarthi-roadpack, version 1, title/attribution, nodes, directed edges,
restrictions, warnings and optional places. Restrictions pair incoming/outgoing
edge IDs with an only flag; edges carry length/speed/pavement/toll/roundabout data.

A* tracks incoming edges for turn rules. Endpoints snap to nodes within 250 m,
not split positions along edges. Disconnected routes fail. Access hierarchy,
motorcycle one-way overrides and supported via-node rules are imported. Conditional
access and ways in unsupported restrictions are conservatively excluded; review
warnings. Not every country's implicit access law is inferred.

Limits: 100 MB input; 300,000 road nodes, 600,000 directed edges, 300,000 restrictions,
100,000 extra places. Use regional extracts. No OBF/PBF, country/planet runtime,
offline DEM/terrain or full specialised-overlay parity. Retain OSM/ODbL attribution
and source licence. Swift fixtures/performance/real-road acceptance remain pending.
