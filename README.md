# Transit Trips

Reads a CSV of card taps (ON and OFF), matches them into trips, prices each trip, and writes the trips to a CSV.

## How to run

Requires Java 21.

```bash
./mvnw test
```

```bash
./mvnw -q compile exec:java -Dexec.args="<input taps.csv> <output trips.csv>"
```

e.g. for the example from the brief:

```bash
./mvnw -q compile exec:java -Dexec.args="src/test/resources/example/taps.csv trips.csv"
```

## Example output

For the example input in the brief (`src/test/resources/example/taps.csv`). Also saved as 
`src/test/resources/example/trips.csv`, which `AppTest` checks against.

```
Started, Finished, DurationSecs, FromStopId, ToStopId, ChargeAmount, CompanyId, BusID, PAN, Status
22-01-2023 13:00:00, 22-01-2023 13:05:00, 300, Stop1, Stop2, $3.25, Company1, Bus37, 5500005555555559, COMPLETED
22-01-2023 09:20:00, , , Stop3, , $7.30, Company1, Bus36, 4111111111111111, INCOMPLETE
23-01-2023 08:00:00, 23-01-2023 08:02:00, 120, Stop1, Stop1, $0.00, Company1, Bus37, 4111111111111111, CANCELLED
, 24-01-2023 16:30:00, , , Stop2, $0.00, Company1, Bus37, 5500005555555559, UNMATCHED
```

## Design

In the order a tap flows through:

- `CsvUtils` reads taps lazily and writes trips.
- `TripProcessor` sorts taps by time, feeds them to the matcher one at a time, and closes anything still open at the end.
- `TripMatcher` holds the matching rules below. It keeps at most one open ON per card.
- `FareTable` prices a pair of stops in either direction, and gives the max fare from a stop.
- `Tap` and `Trip` are plain records. Money is `BigDecimal` and times are UTC `Instant`s.

Main decisions:

- Taps are matched one at a time, as if they were coming off an event stream partitioned by card, so the matcher 
  only holds state for open trips. Sorting is done up front because the input is a finite file (see Assumptions).
- Max trip length so an abandoned ON can't be matched to an OFF days later. I used tap time as the clock, which keeps 
  runs deterministic and easy to test. ONs expire as later taps come in, so the matcher only holds about a day of open 
  trips. This is to attempt to keep memory use optimal.
- Cancellation window to stop a late same-stop OFF from becoming a free ride.
- Duplicate window so a card read twice doesn't charge the max fare.
- No dependencies beyond JUnit and AssertJ.

Matcher tests are tagged with the scenario number (S#n) from the table below. `AppTest` runs the brief's example end to end.

## Scenarios

Per card (PAN). Max trip length is 24 hours, cancellation window is 10 minutes, duplicate window is 1 minute.
"Max fare" is the highest fare from the ON stop.

| # | Taps                                                          | Result                                                                    |
|---|---------------------------------------------------------------|---------------------------------------------------------------------------|
| 1 | ON, then OFF at another stop                                  | COMPLETED, fare between the stops                                         |
| 2 | ON, then OFF at the same stop within 10 minutes               | CANCELLED, $0                                                             |
| 3 | ON, then no more taps                                         | INCOMPLETE, max fare                                                      |
| 4 | ON, then ON again                                             | first is INCOMPLETE (max fare), second starts a new trip                  |
| 5 | OFF with no open ON                                           | UNMATCHED, $0, for review                                                 |
| 6 | ON, then OFF more than 24 hours later                         | INCOMPLETE (max fare) + UNMATCHED ($0)                                    |
| 7 | ON, then OFF at another stop exactly 24 hours later           | COMPLETED; limit is inclusive                                             |
| 8 | several ON/OFF pairs in one day                               | one trip per pair                                                         |
| 9 | ON, then OFF on a different company or bus                    | INCOMPLETE (max fare) + UNMATCHED ($0)                                    |
| 10 | ON, then ON at the same stop, company and bus within a minute | second ON ignored (card read twice)                                       |
| 11 | ON, then other cards' taps move past the 24 hours             | INCOMPLETE straight away, before the end of the file                      |
| 12 | ON, then OFF at the same stop after 10 minutes                | INCOMPLETE (max fare) + UNMATCHED ($0). They rode somewhere and came back |

1 to 5 come from the brief and its example. 6 to 12 are my own assumptions.

## Assumptions

Fares:

- One fare table applies to all companies and buses. The fares are hard-coded, no need to make it configurable.

On the input csv file:

- The input is well-formed with no missing data.
- I'm guessing it represents the data one worker would get from a queue partitioned by card (PAN), which would 
  handle the horizontal scaling side of things.
- There is no guarantee that events are in order (as in real world event streaming where events can be late). 
  Since this is a finite file, I've sorted taps by time before matching. I'm keeping any kind of reorder buffer and
  lateness windows out of scope.
- Taps in the same second: ON goes before OFF, since an OFF can't come before its own ON. Tap ID then keeps the order stable.
- There are no late or "earlier" events relating to taps in this file coming in from another window.

Tap-to-trip matching rules (the reasoning behind the scenarios):

- A tap ON and tap OFF only belong to the same trip if they're on the same card (PAN), company and bus. (S#9)
- A second ON for the same card before any OFF means the first trip was never finished, so it's incomplete. (S#4)
- An OFF with no matching ON (tap 6 in the example) needs flagging for review, so it's UNMATCHED with a $0 charge 
  instead of INCOMPLETE. (S#5)
- Trips can't run forever. Maximum trip length is 24 hours. This is per trip, not per day, so a card can still make 
  several trips in a day. (S#6 to S#8)
- An OFF at the same stop as the ON only counts as CANCELLED within 10 minutes of the ON. After that it's treated as a 
  round trip rather than a free ride, preventing abuse when doing multiple trips. (S#2, S#12)
- A repeated ON at the same stop, company and bus within a minute is treated as one tap (e.g. the card was read twice). 
  Repeated OFFs aren't deduplicated: the trip is already closed, so a repeated OFF is just UNMATCHED ($0, for review) 
  and can't overcharge anyone. (S#10)

Output csv:

- No required sorting for the output csv. I'm assuming these are potentially downstream events that are handled 
  atomically and are not dependent on order. However, it should be stable for a given input.

Bad data:

- A tap at a stop that's not in the fare table is logged as a warning and skipped, and the rest of the file is still 
  processed. I'm assuming that any fare handling around these kinds of data errors is out of scope.

## Out of scope

- A reorder buffer with a lateness window, for a live stream.
- Sending skipped taps somewhere for review, e.g. a dead-letter queue.
- Masking card numbers (PAN). They're in the clear everywhere for simplicity's sake.
