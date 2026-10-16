# Transit Trips

Work in progress. Right now this is mostly my notes and initial assumptions from reading the brief, 
plus a project skeleton.

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

### Run the app (not implemented yet):

```bash
./mvnw -q compile exec:java -Dexec.args="<input taps.csv> <output trips.csv>"
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

The full output for the example input will go in once the logic is done.

## Scenarios

For a single card (PAN). Max trip length is 24 hours.

| # | Scenario | Result |
|---|---|---|
| 1 | ON, then OFF at another stop | COMPLETED, fare between the stops |
| 2 | ON, then OFF at the same stop | CANCELLED, $0 |
| 3 | ON, then no more taps for that card | INCOMPLETE, max fare from ON stop |
| 4 | ON, then ON again | INCOMPLETE for the first, second ON starts a new trip |
| 5 | OFF with no ON before it | UNMATCHED, $0, for review |
| 6 | ON, then OFF after the max trip length | INCOMPLETE (max fare from ON stop), then UNMATCHED ($0) |
| 7 | ON, then OFF exactly at the max trip length | COMPLETED or CANCELLED as normal |
| 8 | several ON/OFF pairs in one day | each pair is its own trip |

### Some nice to haves (Might implement if I find time)

- Matching on company and bus. Right now an ON and OFF are matched on the card alone. I'll assume for now that this is fine.
- Duplicate taps. A double tap at the same stop would currently close the first ON as INCOMPLETE and charge the max fare. 
  Ideally we treat ONs in the same stop within a minute as one tap.
- Expiring abandoned ONs in case the "stream" is long or never ending (if we pretend the file is an event stream from a data pipeline)
- 
## Assumptions (WIP - may modify later)

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
- There are no late events relating to any taps in this csv window coming in a later window
- There are no "earlier" events relating to any taps in this csv window coming in from a prior window

Tap-to-trip matching business rules:

- A tap ON and tap OFF are matched on the card (PAN) alone for now 
- A second ON for the same card for any bus before any OFF means the first trip was never finished, so it's incomplete. 
- An OFF with no matching ON (tap 6 in the example). Needs flagging for review, $0 charge, UNMATCHED instead of INCOMPLETE
- Trips can't run forever. Maximum trip length is 24 hours. If an OFF comes more than 24hrs after the ON, 
  the ON is INCOMPLETE and the OFF is UNMATCHED. This is per trip, not per day, so a card can still make several trips in a day.

Output csv: 

- INCOMPLETE trip: Finished, ToStopId, and DurationSecs should be empty.
- UNMATCHED trip: Started, FromStopId, and DurationSecs should be empty. Charge is $0.00.
- No required sorting for the output csv. I'm assuming these are potentially downstream events that are handled 
  atomically and are not dependent on order. However, it should be stable for a given input. 

Other considerations: 
- One fare table applies to all companies and buses. The fares are hard-coded, no need to make it configurable.
- Can there be stops that are not in the fare table? other invalid data?
- Card numbers (PAN) aren't masked anywhere and I won't be bothering with a masking layer for simplicity's sake. 