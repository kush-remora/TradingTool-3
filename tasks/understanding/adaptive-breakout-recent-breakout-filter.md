# Adaptive Breakout — Recent Broken Bucket

The Adaptive Breakout scanner already identifies rows whose active ceiling has been broken and shows the original fresh-breakout date. The requested workflow is to isolate only those already-broken rows whose breakout happened within the last 30 calendar days, measured from each row's latest completed scan date.

The existing `Already broken` status remains unchanged. A separate `Broken ≤30d` status bucket filters only `BREAKOUT_CONTINUATION` rows with a breakout age from 0 through 30 days, and each already-broken row now shows its age beside the breakout date.
