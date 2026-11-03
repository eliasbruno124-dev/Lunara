# Automatic period end

Without an end date entered by you, Lunara assumes five calendar days. The
start day counts as the first day: a period starting on 1 November is assumed
to end on 5 November; from 6 November on it no longer counts as ongoing.
This also applies to older entries and works without a background job.

An end you entered yourself always takes precedence, even if the period lasts
longer. The end can be corrected later. Entries that genuinely overlap are
still rejected. The app, the web app, the server and the database check all use
the same limit.

An assumed end is not a confirmed observation. The original entry is kept;
export, synchronization and encrypted backups keep an end that was not entered
as unknown. The analysis does not learn a supposedly measured five-day length
from it. The calendar never shows future assumed days as already recorded.

## Fertility

The existing calculation takes uncertainty into account. Its permitted results
are at most 19 days wide. Older, wider stored estimates are rejected rather
than shortened to a seemingly precise length. The current overview hides an
estimate after its own end date. Valid historical calendar markings stay
separate from today's overview.

This limit is a consistency check of the software, not a medical validation.
Calendar-based estimates do not confirm ovulation and are not suitable for
contraception. See also the
[NHS information](https://www.nhs.uk/contraception/methods-of-contraception/natural-family-planning/).
