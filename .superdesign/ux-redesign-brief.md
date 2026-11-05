# NaviSarthi mobile UX redesign brief

## Goal
Make trip planning, Tripper connection, and ride progress readable and immediately understandable. Preserve native Android behavior, real vehicle accents, and the existing NaviSarthi logo.

## Current friction
- Home gives Start navigation, Open dash view, and Open Google Maps similar visual prominence.
- Start navigation opens an empty route when no location is selected. Destination acquisition actually happens through Google Maps sharing; the label skips this prerequisite.
- Small uppercase labels and #65686B secondary text on dark surfaces reduce readability.
- Route preview mixes primary trip decisions with voice, mode, downloads, saved itineraries, and technical ETA explanation.
- Connection state needs an obvious next action, rather than Offline alone.

## Proposed flow
1. Home: Where are you riding? Primary Choose destination opens the existing Google Maps workflow. Show a concise two-step Share > NaviSarthi explanation, saved places, and a compact Tripper status card.
2. Destination received: show the named place, map, ordered stops, total distance and estimated time. Primary Start navigation. Put voice and downloads behind Ride options; show short estimate caveat with expandable details.
3. Connect: explain Turn on your Tripper, then show the actual Wi-Fi > authentication > streaming progress. Request necessary permissions at the moment of use with a clear reason and retry path.
4. Riding: large next maneuver, distance, arrival estimate, honest connection state, circular TFT preview. Controls below: voice, recenter, stop. Confirm ending the ride.
5. Rides: recent journey summaries and legible distance/time values. Garage remains optional. Settings remains secondary.

## Visual direction
Warm charcoal, crisp off-white, generous spacing, restrained gold by default with existing blue/mint/orange vehicle themes. Use Geist for labels/body and Geist Mono only for telemetry. Body 16sp, supporting copy 14sp minimum, headings 24-30sp. Aim for 4.5:1 text contrast, 48dp minimum tap targets, scalable text, and status words/icons in addition to color.

## Guardrails
Do not invent in-app destination search, live traffic, bike telemetry, or unsupported bike compatibility. Google Maps sharing is the existing destination entry method. Keep planning and in-ride interfaces distinct. Show sample route values only as prototype data. Preserve source code while designs are reviewed.
