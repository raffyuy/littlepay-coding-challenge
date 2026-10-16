# Transit Trips

## The problem

Passengers tap a credit card (identified by its PAN) when they get on a bus and again when they get off. 
Given a CSV of taps, this application should match the ONs and OFFs into trips, work out what to charge for each one, 
and write the trips to another CSV.

Fares (same price in either direction):

- Stop1 <-> Stop2: $3.25
- Stop2 <-> Stop3: $5.50
- Stop1 <-> Stop3: $7.30

Trip types:

- Completed: ON at one stop, OFF at a different stop. Charge the fare between them.
- Incomplete: ON with no OFF. Charge the highest fare possible from the ON stop.
- Cancelled: ON and OFF at the same stop. Charge nothing.

The brief says to focus on the matching and pricing logic over the CSV handling if time is short.

## How to run


### Requirements: 
- Java 21

### Run the tests:

```bash
./mvnw test
```

### Run the app:

```bash
./mvnw -q compile exec:java -Dexec.args="<input taps.csv> <output trips.csv>"
```

e.g. for the example from the brief:

```bash
./mvnw -q compile exec:java -Dexec.args="src/test/resources/example/taps.csv trips.csv"
```

## Sample input

From the brief, saved as `src/test/resources/example/taps.csv`:

```
ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN
1, 22-01-2023 13:00:00, ON, Stop1, Company1, Bus37, 5500005555555559
2, 22-01-2023 13:05:00, OFF, Stop2, Company1, Bus37, 5500005555555559
3, 22-01-2023 09:20:00, ON, Stop3, Company1, Bus36, 4111111111111111
4, 23-01-2023 08:00:00, ON, Stop1, Company1, Bus37, 4111111111111111
5, 23-01-2023 08:02:00, OFF, Stop1, Company1, Bus37, 4111111111111111
6, 24-01-2023 16:30:00, OFF, Stop2, Company1, Bus37, 5500005555555559
```

## Sample output

The brief only gives the trip for taps 1 and 2:

```
Started, Finished, DurationSecs, FromStopId, ToStopId, ChargeAmount, CompanyId, BusID, PAN, Status
22-01-2023 13:00:00, 22-01-2023 13:05:00, 300, Stop1, Stop2, $3.25, Company1, Bus37, 5500005555555559, COMPLETED
```

Full output for the example input, saved in `src/test/resources/example/trips.csv`:
```
Started, Finished, DurationSecs, FromStopId, ToStopId, ChargeAmount, CompanyId, BusID, PAN, Status
22-01-2023 13:00:00, 22-01-2023 13:05:00, 300, Stop1, Stop2, $3.25, Company1, Bus37, 5500005555555559, COMPLETED
22-01-2023 09:20:00, , , Stop3, , $7.30, Company1, Bus36, 4111111111111111, INCOMPLETE
23-01-2023 08:00:00, 23-01-2023 08:02:00, 120, Stop1, Stop1, $0.00, Company1, Bus37, 4111111111111111, CANCELLED
, 24-01-2023 16:30:00, , , Stop2, $0.00, Company1, Bus37, 5500005555555559, UNMATCHED
```

## Scenarios

For a single card (PAN). Max trip length is 24 hours, cancellation window is 10 minutes, duplicate window is 1 minute.

| # | Scenario | Result |
|---|---|---|
| 1 | ON, then OFF at another stop | COMPLETED, fare between the stops |
| 2 | ON, then OFF at the same stop within 10 minutes | CANCELLED, $0 |
| 3 | ON, then no more taps for that card | INCOMPLETE, max fare from ON stop |
| 4 | ON, then ON again | INCOMPLETE for the first, second ON starts a new trip |
| 5 | OFF with no ON before it | UNMATCHED, $0, for review |
| 6 | ON, then OFF after the max trip length | INCOMPLETE (max fare from ON stop), then UNMATCHED ($0) |
| 7 | ON, then OFF exactly at the max trip length | COMPLETED or CANCELLED as normal |
| 8 | several ON/OFF pairs in one day | each pair is its own trip |
| 9 | ON, then OFF on a different company or bus | INCOMPLETE (max fare from ON stop), then UNMATCHED ($0) |
| 10 | ON, then ON again at the same stop, company and bus within a minute | second ON ignored as a duplicate (card read twice) |
| 11 | ON, then nothing for that card while other taps move past the max trip length | INCOMPLETE as soon as a later tap shows the max has passed, instead of waiting for the end of the file |
| 12 | ON, then OFF at the same stop more than 10 minutes later | INCOMPLETE (max fare from ON stop), then UNMATCHED ($0). They rode somewhere and came back, so it's not a free cancellation |

1 to 5 come from the brief and its example. 6 to 12 are my own rules from assumptions.
Cards are matched independently, so taps from other cards in between don't affect any of these.

## Assumptions - (may still be refined)

Things I'm taking as given:

- The input is well-formed with no missing data.
- All times are UTC.
- All trips are within the same geolocation. (i.e. no need to think about multiple cities/currencies/timezones/etc) 
- Fares are per pair of stops (where you got on and where you got off), not added up per segment.
  e.g. Stop1 to Stop3 is $7.30, not $3.25 + $5.50.
- Every pair of stops has a fare.

On the input csv file: 

- I'm guessing it represents the data one worker would get from a queue partitioned by card (PAN), which would handle the horizontal scaling side of things. 
- There is no guarantee that events are in order (as in real world event streaming where events can be late) 
- Since this is a finite file, taps are sorted by time before matching. In a live prod event stream 
  this would need a reorder buffer that holds taps for a lateness window instead.
- Taps in the same second: ON goes before OFF, since an OFF can't come before its own ON.  Tap ID then keeps the order stable.
- There are no late events relating to any taps in this csv window coming in a later window
- There are no "earlier" events relating to any taps in this csv window coming in from a prior window

Tap-to-trip matching business rules:

- A tap ON and tap OFF only belong to the same trip if they're on the same card (PAN), company and bus. 
- A second ON for the same card for any bus before any OFF means the first trip was never finished, so it's incomplete. 
- An OFF with no matching ON (tap 6 in the example). Needs flagging for review, $0 charge, UNMATCHED instead of INCOMPLETE
- Trips can't run forever. Maximum trip length is 24 hours. If an OFF comes more than 24hrs after the ON, 
  the ON is INCOMPLETE and the OFF is UNMATCHED. This is per trip, not per day, so a card can still make several trips in a day.
- An OFF at the same stop as the ON only counts as CANCELLED within 10 minutes of the ON. After that it's treated as a 
  round trip (INCOMPLETE + UNMATCHED) rather than a free ride. (Preventing abuse when doing multiple trips)
- A repeated ON at the same stop, company and bus within a minute is treated as one tap (e.g. the card was read twice). 
  Repeated OFFs aren't deduplicated: the trip is already closed, so a repeated OFF is just UNMATCHED ($0, for review) 
  and can't overcharge anyone.

Output csv: 

- INCOMPLETE trip: Finished, ToStopId, and DurationSecs should be empty.
- UNMATCHED trip: Started, FromStopId, and DurationSecs should be empty. Charge is $0.00.
- No required sorting for the output csv. I'm assuming these are potentially downstream events that are handled 
  atomically and are not dependent on order. However, it should be stable for a given input. 

Other considerations: 
- One fare table applies to all companies and buses. The fares are hard-coded, no need to make it configurable.
- A tap at a stop that's not in the fare table is logged as a warning and skipped, and the rest of the file is still processed. 
  I'm assuming that any fare handling around these kinds of data errors is out of scope. 
- Card numbers (PAN) aren't masked anywhere and I won't be bothering with a masking layer for simplicity's sake. 