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

## Assumptions (WIP - may modify later)

Things I'm taking as given:

- The input is well-formed with no missing data.
- All times are UTC.
- All trips are within the same geolocation. (i.e. no need to think about multiple cities/currencies/timezones/etc) 

On the input csv file: 

- There is no guarantee that events are in order (as in real world event streaming where events can be late)
- There are no late events relating to any taps in this csv window coming in a later window
- There are no "earlier" events relating to any taps in this csv window coming in from a prior window

Tap-to-trip matching business rules:

- A tap ON and tap OFF belong to the same trip only if the PAN, CompanyId and BusID all match.
- A second ON for the same card for any bus before any OFF means the first trip was never finished, so it's incomplete. 
- An OFF with no matching ON (tap 6 in the example). Needs flagging for review, $0 charge, UNMATCHED instead of INCOMPLETE
- Trips can't run forever. There should be a maximum trip length (probably within a day, still deciding on the number).
  I'm thinking if an ON comes in and the OFF comes after 24hrs, it should probably count the ON as incomplete,
  and the OFF as orphaned and needs review.
- Duplicate (same tap recorded twice) or multiple taps in the same stop in a short span of time - treat as one tap. 
  - If time diff < 1 minute, treat as one tap
  - If time diff > 1 minute, treat as two trips, where the first is an INCOMPLETE trip, and the second is a new trip started. 

Output csv: 

- INCOMPLETE or UNSTARTED trip: Finished, ToStopId, and DurationSecs should be empty. UNSTARTED charge is $0.00.
- No required sorting for the output csv. I'm assuming these are potentially downstream events that are handled 
  atomically and are not dependent on order. However, it should be stable for a given input. 

Other considerations: 

- Can there be stops that are not in the fare table? other invalid data?
- Card numbers (PAN) aren't masked anywhere and I won't be bothering with a masking layer for simplicity's sake. 