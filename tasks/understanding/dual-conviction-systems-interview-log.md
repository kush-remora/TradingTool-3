# Dual Conviction Systems — Interview Log

This file is the source of truth for discovery. It captures the interview trail in question/answer form so we do not mutate the HLD on every answer and accidentally create inconsistencies. The HLD should be compiled from this log after the interview is sufficiently complete.

## Status

- Interview sweep is complete for the current HLD pass.
- The stable compiled HLD is [docs/architecture/dual-conviction-systems-hld.md](/Users/kushbhardwaj/Documents/github/TradingTool-3/docs/architecture/dual-conviction-systems-hld.md).
- This file remains the raw discovery history. Future revisions can still be appended here before the next HLD refresh.

## Captured Q&A

### 1. Core product shape

**Q:** Are we building one system or two?

**A:** Two different systems:
- Forward High Conviction
- Backward system

The user described them as trader + investor-cum-trader approaches.

### 2. Relationship between accumulation and Phase D

**Q:** Can accumulation ending and Phase D starting happen at the same time in the Wyckoff lifecycle?

**A:** Yes, the ending of accumulation and the start of Phase D can happen on the same or adjacent sessions. The user explicitly accepted the idea that accumulation may be ending and Phase D may just have started.

### 3. Lifecycle modeling

**Q:** Should the lifecycle be represented as explicit states rather than forcing a single rigid phase label?

**A:** Yes. The user leaned toward a state-transition style such as:
- accumulation
- accumulation exhausted
- markup
- momentum bought
- end lifecycle / exit

### 4. Entry timing

**Q:** Is buying allowed only at one setup, or can the user enter at different stages?

**A:** Entry can happen at different setups.

The user clarified:
- sometimes buy during accumulation only
- ideal flat-line accumulation can justify an early buy and longer hold
- sometimes, in the backward flow, buy at Phase D

### 5. Trade actions manual or automatic

**Q:** Should buy/sell stages be inferred automatically from market evidence?

**A:** No. Trade actions are pure manual actions.

### 6. Data ingestion mode

**Q:** Will data ingestion be automatic or manual?

**A:** Pure manual. All Chartink CSVs and the Groww volume shocker file will be updated daily by hand.

### 7. Partial external batch handling

**Q:** Can the system run with partial external file data?

**A:** No. Partial external data will distort results, so the batch should not run.

### 8. Shared vs separate upload batches

**Q:** Do Forward and Backward have separate daily file batches?

**A:** No. Both systems need one shared file batch.

### 9. Daily external file manifest

**Q:** Which files are part of the shared daily batch?

**A:** The user listed:
- Groww volume shock
- minimum-volume Chartink scanners: LVQ, LV100, LVY
- Phase D
- phase ignition: large, mid, small
- accumulation: large, mid, small, multiplied by two passes

### 10. Rule split inside `phase-d-markup-chartink.md`

**Q:** Which rules belong to Phase D and which belong to Momentum?

**A:** In `.claude/requirements/strategies/52w-momentum/phase-d-markup-chartink.md`:
- Rules 1 to 4 are Phase D
- Rule 5 is Momentum

### 11. Delivery data source and responsibility

**Q:** Does Chartink provide delivery data?

**A:** No. Chartink does not have delivery data. Delivery analysis will be done locally.

### 12. Is missing delivery data a blocker?

**Q:** If local delivery data is missing, should the entire run stop?

**A:** No. The system should call out that delivery data is missing, but this is not a blocker.

### 13. Upload completion vs processing trigger

**Q:** When the shared daily batch is fully uploaded, should processing start automatically on the last valid file upload, or should there be an explicit `Run` action?

**A:** No automatic processing. Upload and processing are separate workflows.

The user clarified that the upload workflow is not just a staging step. Its responsibility is to:
- capture all input data
- expose current-day and past-day data for each input source
- preserve source-to-source linking over time
- make it easy to inspect all footprints for a stock
- make it easy to see every time a stock appears in a particular source document

Processing/evaluation should not piggyback on upload completion.

### 14. Missing candle history behavior

**Q:** If Kite candle history is missing for one stock during evaluation, should the system skip only that stock and continue the batch, or reject the whole day's run?

**A:** This should effectively never happen on an open market day. If Kite candle retrieval fails, the system should stop and be investigated.

The only expected exception mentioned was a market holiday.

### 15. Visibility of missing-candle failures

**Q:** If one stock is skipped because candle history is missing, should it still appear in the UI with a visible `missing data` status, or should it be hidden completely until data exists?

**A:** Do not hide it. Treat the condition as a visible bug so it can be fixed quickly. Hiding it would hide the system issue.

### 16. Evaluation trigger and execution model

**Q:** After uploads are complete, should evaluation start only when you explicitly press `Run`, or should there be some later automatic trigger like end-of-upload-day finalization?

**A:** Evaluation belongs to a separate UI and separate sub-workflow. It should not auto-run from upload completion.

The user clarified:
- Forward and Backward should be runnable independently
- a user may run only one workflow and stop there
- a user may run only a subset of a workflow, such as validating Phase D only in Backward
- the pipeline should expose intermediate results after each step
- each step should be independently runnable and inspectable
- the system should avoid a black-box model where only the final result is visible

### 17. Evidence view scope

**Q:** In the upload workflow, do you want the stock-centric evidence view to be shared across both systems as one common evidence history, or do you want separate Forward/Backward evidence views?

**A:** Answered by the previous clarification: upload-side evidence visibility is common/shared, and run-side workflow views are the separate concern.

### 18. Convergence window configurability

**Q:** For the Backward system, is the 5-trading-session convergence window final, or do you want it configurable?

**A:** Configurable.

The user clarified a broader rule: anything expressed in days and likely to change should live in a master JSON config file.

### 19. Forward pipeline step meaning

**Q:** In the Forward workflow, what are the exact pipeline steps you want exposed separately?

**A:** The user partially confirmed and refined the proposed step breakdown:

- Step 1 is to read accumulation inputs and determine where accumulation exists and how long it lasts
- Step 2 is to classify which shape of accumulation each stock has
- The previously suggested equilibrium step is unclear to the user and may already be covered inside Phase 1
- The remaining later steps generally looked acceptable to the user

This means the Forward pipeline should definitely expose:
- accumulation detection / accumulation length discovery
- accumulation shape classification

And it should not assume a separate equilibrium step until its purpose is clarified.

### 20. Backward pipeline step meaning

**Q:** In the Backward workflow, what are the exact pipeline steps you want exposed separately?

**A:** The user said the remaining step model looked good.

Working accepted shape:
- raw Phase D source evidence view
- source-specific noise gates
- configurable convergence window
- backward lookup for base/cause
- final forensic result

### 21. Same-day rerun behavior

**Q:** When a pipeline step is run again after config or data changes, should the old result be preserved as history or overwritten by the latest run result?

**A:** Use one visible run per day. If the workflow or step is run multiple times on the same day, the old result should be replaced by the new one.

### 22. Forward context/equilibrium step

**Q:** Should Forward have a separate context/equilibrium step after shape classification?

**A:** No separate step.

The user chose the simplest interpretation:
- accumulation detection / accumulation length
- shape classification
- later validation steps

This means the previously suggested standalone equilibrium/context step should be removed from the Forward pipeline unless later discovery proves a concrete need.

### 23. Forward output structure

**Q:** For the final Forward output, should there be one combined watchlist or separate visible candidate sets?

**A:** Separate visible candidate sets are required.

The user wants distinct outputs such as:
- raw accumulation candidates
- valid accumulation candidates
- high-conviction research candidates

Reason given by the user:
- visibility is the most important requirement
- each step must be independently justifiable
- separate outputs help detect bugs
- separate outputs help validate business logic
- separate outputs help identify business gaps and mature the product over time

### 24. Visibility as a design principle

**Q:** Is step-level visibility mainly a UI preference, or a core product/validation requirement?

**A:** It is a core product and validation requirement.

The user explicitly emphasized:
- visibility is essential for validating logic
- visibility is essential for finding bugs
- visibility is essential for improving business logic
- each step should be explainable and justified

This should be treated as a first-class architecture principle, not a cosmetic UI preference.

### 25. Forward validity boundary

**Q:** For the Forward workflow, what exactly moves a stock from `raw accumulation` to `valid accumulation`?

**A:** Shape validation only.

The user explicitly clarified:
- delivery should help build conviction
- delivery should not be used to reject Forward validity at this stage

So the working Forward progression is:
- raw accumulation candidate
- valid accumulation candidate after shape validation
- later conviction building from delivery, LVQ/LV100-type evidence, and research

### 26. High-conviction promotion

**Q:** What moves a stock from `valid accumulation` to `high conviction research`?

**A:** Both system evidence and manual research contribute.

System-side examples named by the user:
- volume evidence
- LVQ / LV100 style evidence
- delivery data

Manual/research-side contribution:
- research can determine whether a stock is effectively dead
- research can determine whether the stock is becoming a high-growth candidate
- notes and qualitative judgment materially matter at this stage

### 27. Position tracking fields and source

**Q:** What fields must be captured at `buy` and `exit`?

**A:** The user named these core position fields:
- date
- quantity
- average buying price
- reason
- stop-loss percent
- target percent

But the user also clarified that this may not be a manual-entry workflow.

Preferred direction:
- use a separate UI that reads current Kite holdings
- derive buy date, quantity, average price, and related holding facts from Kite
- also use GTT data when available to understand target or stop-loss state
- show live holding status such as profit/loss and stop-loss context

Important implication:
- the system is not using Kite to place orders
- Kite is being considered as the source of truth for tracking holdings and buy/sell state
- the final exit/closed-position data model still needs clarification

### 28. Expired but unbought Forward cases

**Q:** If a Forward case never gets bought and its accumulation chain expires, should it remain in history or disappear?

**A:** It should remain visible and should not disappear.

The user clarified the business reason:
- capital is limited, so not every valid signal can be bought
- signals must be prioritized
- the user may still buy after a few days of Phase D
- good flat accumulations can keep a useful Phase D run alive for roughly 15-20 days

Implication:
- lack of a buy does not make the case irrelevant
- a Forward case must remain visible beyond the ideal earliest entry point

### 29. Open vs closed position tracking scope

**Q:** If Kite holdings are the source of truth, do you want the system to track only current open holdings, or also closed/sold positions historically?

**A:** Current open positions are the immediate priority, but closed/sold positions will also be needed.

The user explicitly noted that without sold-position history, the system cannot fully know when an exit happened or reconstruct full entry/exit tracking.

This means:
- open holdings are phase-1 priority
- closed-position/history support is expected, even if it lands later

### 30. New campaign after a prior sold case

**Q:** If a stock is sold and later forms a new accumulation campaign, should that become a brand new case while preserving the old history separately?

**A:** Yes. It should be a new case.

### 31. Upload-side stock detail view

**Q:** When opening one stock in the upload-side evidence browser, should it show only raw source appearances or also derived linkages?

**A:** It should show both raw data and linkage/derived connections.

The user explicitly wants this so the system can validate:
- the raw input itself
- how the system linked and interpreted that input through the flow

### 32. Exit detection source

**Q:** If Kite holdings no longer show a stock, should the system ask for confirmation or use another explicit source before marking exit?

**A:** Exit detection should be automated from Kite history.

This means the preferred model is:
- current holdings come from Kite holdings
- entry/exit reconstruction should use Kite history
- exit state should not depend on manual confirmation as the default path

### 33. Upload-side navigation entry paths

**Q:** In the upload-side evidence browser, should exploration start from source documents, from stocks, or from both?

**A:** Both paths should exist.

The user clarified the reason:
- early testing may focus on one stock at a time
- later live usage and backtesting-style review may start from dates/documents

### 34. CSV export rule

**Q:** Should CSV export be supported only in analysis-heavy screens or everywhere?

**A:** Every UI should allow CSV export.

This is a global product rule and should apply across upload views, run views, candidate lists, and related inspection screens.

### 35. Run-time date scope

**Q:** When a pipeline step is run, should its output exist only for today or also be reopenable for past dates?

**A:** The user clarified that uploaded files contain today's data plus roughly the last nine months of historical data, and the UI should support running for:
- today only
- a recent window such as the last 30 days
- the full available period

This means run execution should be date-scope aware rather than hardcoded to a single-day model.

### 36. Upload-side vs run-side boundary

**Q:** Is this boundary correct: upload-side = raw evidence plus linkage validation, run-side = step execution plus candidate outputs?

**A:** Yes.

### 37. Backward step visibility shape

**Q:** In Backward, after `phase d source -> noise gates -> convergence`, do you want separate visible candidate lists for each step as well, just like Forward?

**A:** Yes. Separate views are required.

Working interpretation:
- Backward still needs stepwise execution and validation
- step outputs should remain separately visible for validation
- this follows the same visibility-first principle used in Forward

### 38. Run result replacement rule

**Q:** How should reruns affect stored run results?

**A:** Keep it simple. For a given date, if a result already exists, the new run replaces it.

The user explicitly wants:
- a maximum of one result per date
- replacement instead of accumulating multiple same-date run snapshots
- this to prevent data from getting into a bad shape

### 39. Run date scope controls

**Q:** Should the run UI support only presets or also a custom date range?

**A:** A custom date-range picker is required.

The user also clarified:
- config should allow default date settings
- current date should always be allowed to run

### 40. Full-period output shape

**Q:** When running a larger period, should outputs be visible day by day or only as one latest-state result?

**A:** Outputs should be visible day by day.

The user wants:
- a single UI showing multiple dates and counts
- clickable detail for each date
- the ability to inspect one stock and see on which dates it appeared in the run output

### 41. HLD simplicity rule

**Q:** Should the current interview lock down detailed workflow behavior now, or remain revisable in LLD/implementation?

**A:** This is still HLD, so workflow details can be revised later during LLD or implementation.

The user explicitly emphasized:
- keep the design simple
- remember this is a single-user system
- avoid over-engineering
- avoid making the design overly verbose
- refine workflow detail later if implementation reality demands it

### 42. High-conviction promotion style in Forward

**Q:** For Forward, should `high conviction research` be set manually, or can the system auto-promote a stock once enough evidence is present?

**A:** The system should drive high-conviction ordering itself, but keep it simple.

The user explicitly wants:
- flat-shape accumulations to get first priority
- if multiple flat accumulations exist, prefer bigger accumulation length
- if still tied, prefer higher delivery conviction and more LVQ-style days
- no complex ranking system
- simple prioritization/order, not an over-engineered score

### 43. Backward final output buckets

**Q:** For Backward final output, what are the main result buckets you want?

**A:** Broadly aligned with the suggested examples, but not finalized at this point.

### 44. Cross-system visibility

**Q:** When a stock appears in both Forward and Backward around the same time, should the UI show any cross-reference hint?

**A:** No. Both systems should remain separate.

### 45. Groww shocker inside Forward

**Q:** Should Forward use Groww Volume Shocker anywhere in its pipeline?

**A:** Yes. Forward should use Groww Volume Shocker in the later Phase D part of its pipeline to support conviction that the stock is definitely in Phase D.

### 46. High-conviction persistence in Forward

**Q:** After a stock becomes `high conviction`, should it remain there until manually downgraded, or should the system automatically move it out if later evidence weakens?

**A:** Keep it there for now.

The user clarified:
- age/date visibility will help interpret staleness
- if systematic weakness handling is needed later, that should become a dedicated downgrade workflow

### 47. Backward core success condition

**Q:** What is the main success condition for Backward?

**A:** The move should be backed by an accumulation period.

The user clarified:
- it does not have to be the strongest possible accumulation
- but there does need to be an accumulation period behind the Phase D move

### 48. Forward `Phase D started` output

**Q:** Should Forward have a dedicated visible `Phase D started` bucket/list after `high conviction`?

**A:** Refined later: not as one single bucket/step.

The user clarified that the later Forward flow should be split into multiple separate user-visible steps rather than collapsed into one `Phase D started` state.

Examples named by the user:
- Ignition
- Phase D
- Volume Shocker

These should be treated as distinct steps/user stories with visibility at each stage.

### 49. Forward late-stage step granularity

**Q:** Should late Forward progression be modeled as one Phase D confirmation step or as multiple visible steps?

**A:** Multiple visible steps are required.

The user also clarified that even before the later Phase D progression, conviction-building itself has multiple distinct steps such as:
- accumulation
- stock footprint signals like LVQ
- volume going down / dry-up behavior
- delivery abnormality
- high-volume confirmation

These should not be collapsed into one black-box step.

### 50. Wyckoff breakout / last-resistance step

**Q:** Is there a distinct Wyckoff step where the stock crosses the last key resistance and enters the obvious bull-run zone?

**A:** Yes. This is an important explicit step.

The user described it as:
- a Kotlin-logic-driven check
- the point where the stock crosses the last resistance
- the point from which the stock is in a more obvious full run / bull run
- a visibility aid showing "if stock crosses this price, bull run likely starts"
- potentially the last attractive buy zone before broad market participation

This implies the Forward flow needs an explicit breakout/resistance-crossing stage, not just generic Phase D labeling.

### 51. Supporting evidence ordering in Forward

**Q:** Are signals like LVQ, dry-up/volume contraction, delivery abnormality, and high-volume confirmation mandatory ordered steps?

**A:** No. They are supporting evidence, not rigid ordered gates.

The user clarified:
- they can appear before Phase D
- they can appear during Phase D
- they should support interpretation rather than define a strict sequence

### 52. Groww shocker timing in Forward

**Q:** Where can Groww Volume Shocker appear inside the Forward lifecycle?

**A:** It can appear after accumulation, after ignition, during Phase D, or after price crosses the previous resistance.

Working interpretation:
- Groww shocker is a later-stage confirmation signal
- but it is not tied to exactly one fixed step boundary

### 53. Breakout visibility fields

**Q:** Should the UI show breakout-related fields like exact resistance price and distance-to-breakout across the flow?

**A:** Yes. This is very important and should stay visible at each step.

The user specifically confirmed visibility for:
- exact resistance price
- current distance from that resistance
- breakout state / status

### 54. Forward stage vs evidence presentation

**Q:** Should Forward use main stages plus supporting-evidence badges, or treat every signal as an equal pipeline step?

**A:** Use main stages plus supporting-evidence badges.

### 55. Last-resistance derivation

**Q:** What should define the `last resistance` used for breakout visibility?

**A:** A Kotlin-derived rule based on candle structure.

### 56. Backward result detail level

**Q:** When Backward finds accumulation, should the result be binary only, or should it expose the same descriptive details as Forward?

**A:** It should expose the same descriptive details rather than a binary-only verdict.

The user explicitly wants Backward output to show:
- accumulation period dates
- accumulation length
- shape
- other relevant parameters
- ignition / Phase D information already shown in the Forward workflow

### 57. Forward main stages

**Q:** Is this main-stage model close to correct for Forward: `raw accumulation -> valid accumulation -> high conviction -> ignition seen -> Phase D seen -> breakout near -> breakout done`?

**A:** Yes.

### 58. Required supporting evidence badges

**Q:** Which supporting evidence badges are definitely required at HLD level?

**A:** The current list was accepted:
- LVQ / LV100 / LVY
- volume dry-up
- delivery abnormal
- high volume
- Groww shocker

### 59. Backward field reuse

**Q:** When Backward shows Forward-like details, should it reuse the same field set/UI pattern where possible?

**A:** Yes.

The user wants similar information and reuse where practical, while keeping the systems logically separate.

### 60. Breakout-near rule

**Q:** How should `breakout near` be defined?

**A:** Use a configurable percentage distance below resistance, with the current chosen value set to 6%.

### 61. Breakout-done rule

**Q:** How should `breakout done` be defined?

**A:** Use daily close above resistance as the HLD rule.

The user also wants intraday high visibility so the UI can show when price tried to break resistance but failed to close above it.

### 62. Backward bucket names

**Q:** Are these acceptable HLD-level Backward buckets: `raw phase d`, `gated phase d`, `converged phase d`, `accumulation-backed candidates`?

**A:** Yes.

### 63. Ignition source scope in Forward

**Q:** In Forward, should `ignition seen` come only from the Chartink ignition scanners, or can other evidence also trigger that stage?

**A:** The user first asked for a reminder of the ignition checks from the source strategy doc. This needs to stay tied to the documented ignition logic rather than memory.

### 64. Phase D source scope in Forward

**Q:** For `Phase D seen` in Forward, should that stage require one specific source, or can it be satisfied by any accepted Phase D evidence source?

**A:** Any accepted Phase D evidence source can satisfy it.

### 65. Priority reason visibility

**Q:** Should `high conviction` show why a stock got priority?

**A:** Yes.

### 66. Ignition source scope in Forward

**Q:** Should `ignition seen` stay strict to Chartink ignition logic only?

**A:** No. Ignition can come from:
- Chartink ignition
- delivery shock

### 67. Accepted Phase D evidence sources

**Q:** Is the accepted Phase D evidence set exactly Chartink ignition, Chartink momentum, Groww volume shocker, and local delivery anomaly?

**A:** Yes.

### 68. Priority reason display detail

**Q:** For `high conviction` reason display, should the UI show all matching reasons or only the top reason?

**A:** Show all matching reasons.

### 69. Rejection visibility across steps

**Q:** For `valid accumulation`, should the UI also show why a raw accumulation was rejected by shape validation?

**A:** Yes, definitely. The user wants visible validation reasoning for all phase/pipeline steps, not just this one.

Working interpretation:
- each step should expose pass/fail reasoning
- rejected candidates should remain explainable
- validation visibility is universal across workflows

### 70. Breakout context in Backward

**Q:** After `accumulation-backed candidates`, should Backward also show breakout-context fields like resistance price and breakout distance?

**A:** Yes, it is good to have, but it is not an absolute must-have for Backward.

### 71. Historical rerun capability

**Q:** When viewing a past date's result, should it be read-only or rerunnable from the same screen?

**A:** It should be rerunnable.

The user clarified the reason:
- logic may change later
- historical reruns are required for validation
- the product will rely on extensive backtesting
- the 9-month uploaded data window exists partly to build conviction that the system works

### 72. Historical accumulation chain visibility

**Q:** For `raw accumulation`, if a stock appears multiple times across the uploaded historical period, should the UI show only the latest active chain or all detected chains by date?

**A:** Show all detected chains by date.

The user clarified the broader operating model:
- screens will be used for both live production usage and validation/backtesting
- for live usage, the user mainly wants to see today's data
- for validation, historical chain visibility is required
- example workflow: if one stock shows a volume shocker today, the user may run the Forward flow and inspect when that stock appeared historically

### 73. Post-breakout handling

**Q:** If a stock reaches `breakout done`, should it remain in the same Forward case flow or move to a separate post-breakout view?

**A:** Defer this decision until implementation time.

### 74. Backward date-range summary detail

**Q:** For Backward reruns over a date range, should the UI summarize counts by date only, or counts by date plus source breakdown?

**A:** Both.

### 75. Live-mode default date scope

**Q:** For live mode, should every main screen default to `today` while still allowing date-range switching?

**A:** Yes.

### 76. Document-side repeat visibility

**Q:** In the upload-side evidence browser, should document views show per-date counts and per-stock repeat counts?

**A:** Yes, with date visibility.

Example expectation:
- a stock like BHEL can be shown with repeated appearances across dates within the same source/document

### 77. Run comparison vs replace

**Q:** For validation/backtesting mode, do you want easy comparison between two runs of the same date range after logic/config changes, or is rerun-and-replace enough for HLD?

**A:** Rerun-and-replace is enough.

### 78. File-row validity rule

**Q:** If one uploaded file has row-level errors, should valid rows still be inspectable, or should the whole file remain unusable until fixed?

**A:** File format should be valid for all lines.

Working interpretation:
- the file must be structurally valid across all rows
- invalid rows should not be silently tolerated for batch acceptance
- raw uploaded data should still remain visible for inspection/debugging

### 79. Supporting badge date visibility

**Q:** For supporting badges like LVQ/LV100/LVY, delivery abnormal, and high volume, should badge dates be visible too?

**A:** Yes. Raw data/date visibility is needed here as well.

### 80. Step execution dependency

**Q:** Should a pipeline step be runnable independently whenever inputs exist, or only after prior steps are run?

**A:** Prior steps should be required, because each run needs data from the previous step.

### 81. Dependency handling in run UI

**Q:** If a later step depends on an earlier step that has not been run for that date range, should the UI disable the step or allow click and show an error?

**A:** Show an error.

### 82. High-conviction ordering control

**Q:** For Forward `high conviction`, should the list be auto-ordered by system priority rules or left for manual sorting?

**A:** Let the user sort manually for now.

### 83. Upload-side raw-detail depth

**Q:** For upload-side raw data, is row-level drilldown required now, or is table-level visibility enough for HLD?

**A:** Table-level visibility is enough for now.

### 84. Historical step-output visibility scope

**Q:** For per-step outputs over history, should each step keep day-by-day historical results visible broadly, or only for the selected date range?

**A:** Only for the selected date range should results be visible.

### 85. Breakout-done post-flow handling

**Q:** Should `breakout done` remain inside the same Forward flow for now, with any separate post-breakout treatment decided later?

**A:** Later.

Working interpretation:
- keep this decision deferred
- do not over-specify post-breakout handling at HLD level

### 86. Manual research structure

**Q:** For manual research in Forward, should HLD assume free-form notes only or a lightweight structured tagging model too?

**A:** Use simple notes plus lightweight manual tags such as `A+`, `A`, `B`, `C`.

### 87. Research tag ownership

**Q:** For the `A+ / A / B / C` research tag, should it be completely manual, or can the system suggest a default?

**A:** Completely manual.

### 88. Research continuity across stages

**Q:** If a Forward stock is `high conviction` and later reaches `breakout done`, should the original research note/tag remain attached to the same case history?

**A:** Yes.

### 89. Research tagging scope

**Q:** Do notes/tags also belong in Backward, or is this manual research tagging only for Forward?

**A:** Manual tagging is only for Forward.

### 90. Forward multi-list visibility

**Q:** In Forward, should a stock be allowed to remain visible in multiple stage lists at once for context?

**A:** Yes.

### 91. Backward multi-step visibility

**Q:** In Backward, should a stock be allowed to appear in multiple step outputs at once, or only in its latest reached bucket?

**A:** Yes, it can appear in multiple step outputs at once.

### 92. Date-range count scoping

**Q:** For upload-side document browsing, when a date range is selected, should counts be calculated only for that range or also show lifetime totals?

**A:** Only for the selected date range.

### 93. Forward overlapping stage visibility detail

**Q:** Should `raw accumulation`, `valid accumulation`, and `high conviction` all remain visible together if a stock has progressed that far?

**A:** Yes, because the dates can be different.

### 94. Backward overlapping stage visibility detail

**Q:** Should `raw phase d`, `gated phase d`, and `converged phase d` all remain visible together if a stock has progressed that far?

**A:** Yes.

### 95. Active date-range visibility

**Q:** In selected date-range views, should the active range always be shown clearly at the top so exports and screenshots remain unambiguous?

**A:** Yes.

### 96. Forward stage-date visibility

**Q:** Should each Forward stage list show its own explicit stage date?

**A:** Yes.

Examples accepted:
- raw accumulation date
- valid accumulation date
- high conviction date
- ignition date
- Phase D date
- breakout near date
- breakout done date

### 97. Backward step-date visibility

**Q:** Should each Backward step output also show its own explicit source/step date?

**A:** Yes.

### 98. CSV export context fields

**Q:** Should each CSV export include active selected date range, stage/step-specific dates, and source names so the file is self-explanatory outside the app?

**A:** Yes.

### 99. Primary stage-date choice

**Q:** For Forward, should a stage date show the first hit, latest hit, or both?

**A:** Show the latest date as the primary stage date.

The user clarified:
- a stage such as ignition may trigger first on one date and later on multiple dates
- the latest date should be the headline value
- historical prior hits should still remain inspectable because they help build conviction

### 100. Multi-source Phase D date display

**Q:** For Backward, when multiple accepted Phase D sources hit on different dates, should the UI show one combined date or separate dates per source plus convergence result?

**A:** Show separate dates per source plus the convergence result.

### 101. Empty-date visibility

**Q:** For a selected date range, should weekends/holidays/empty dates appear explicitly with no-result rows?

**A:** No. Show no result rows.

### 102. Supporting-badge headline fields

**Q:** For Forward supporting badges like `LVQ`, `delivery abnormal`, `high volume`, and `Groww shocker`, should each badge use latest date as the headline value with older hits available in history?

**A:** Yes, with an important nuance.

The user clarified:
- latest date should be visible
- history should remain available for conviction
- for repeating signals such as delivery abnormality, count is also important
- seeing that a signal occurred many times in the past builds conviction
- accumulation start date remains a separate anchor from these badge hit dates

Working interpretation:
- supporting badges should expose at least latest date
- repeating badges should also expose count
- historical prior hits should remain inspectable

### 103. Backward convergence date visibility

**Q:** Should Backward convergence explicitly show the contributing source dates inside the convergence window?

**A:** Yes.

Example accepted:
- ignition date
- Groww shocker date
- delivery anomaly date
- convergence result over the configured window

### 104. Document-view repeat display

**Q:** In upload-side document browsing, when one stock appears multiple times in the selected range, should the UI show only count or count plus latest appearance date?

**A:** Show count plus latest appearance date.

Example accepted:
- `BHEL | 7 times | latest: 2026-06-24`

### 105. Backward final bucket naming intent

**Q:** Should the Backward final bucket names stay exactly as-is, or be renamed so their intent is clearer from the label itself?

**A:** The names should clearly communicate intent when read directly. Final naming should favor meaning over internal terminology.

Working HLD direction:
- avoid opaque/internal labels
- use names that make the stage purpose obvious to the user

### 106. Forward rejection reasons

**Q:** Should `valid accumulation` include explicit rejection reasons so rejected raw candidates remain auditable?

**A:** Yes.

Example reasons discussed:
- inverted-U shape
- broken base
- distribution-like structure

### 107. Upload-side stock timeline shape

**Q:** In the upload-side stock view, should one stock show a merged cross-source chronological timeline or only separate source sections?

**A:** Use a merged timeline.

### 108. Backward final bucket names

**Q:** Do these human-friendly Backward bucket names work for HLD: `raw phase d signals`, `validated phase d signals`, `converged phase d signals`, `accumulation-backed phase d candidates`?

**A:** Yes.

### 109. Merged timeline row context

**Q:** In the merged stock timeline, should each event show both source name and source-specific date?

**A:** Yes.

### 110. Forward acceptance-reason summary

**Q:** Besides rejection reasons, should accepted `valid accumulation` also show an acceptance-reason summary?

**A:** Yes, definitely.

Example summaries discussed:
- flat accumulation
- cup accumulation
- base length in sessions

### 111. Forward high-conviction reason summary

**Q:** Should `high conviction` also show why it is high conviction now?

**A:** Yes.

Examples discussed:
- flat shape
- long base
- delivery abnormal count
- LVQ count
- Groww shocker seen

### 112. Resistance value visibility in Forward lists

**Q:** Should `breakout near` and `breakout done` show the exact resistance value used by Kotlin logic directly in the list view?

**A:** Yes.

### 113. Backward final-list summary density

**Q:** For `accumulation-backed phase d candidates`, should list view show a compact but meaningful subset such as accumulation start/end, length, shape, and latest Phase D source dates?

**A:** Yes.

### 114. Forward context persistence in later lists

**Q:** Should later Forward lists such as `high conviction`, `ignition seen`, `Phase D seen`, `breakout near`, and `breakout done` also show accumulation start date and accumulation length so case context is never lost?

**A:** Yes.

### 115. Backward source badges

**Q:** Should Backward list summaries show which accepted Phase D sources are present as labels/badges?

**A:** Yes.

Examples discussed:
- ignition
- momentum
- Groww
- delivery anomaly

### 116. Timeline inclusion of derived events

**Q:** In the merged stock timeline, should system-derived events appear alongside raw document events?

**A:** Yes.

Examples discussed:
- raw LVQ hit
- derived valid accumulation
- derived high conviction
- derived ignition seen

### 117. Timeline default ordering

**Q:** In the merged stock timeline, should rows be ordered newest-first by default?

**A:** Yes. Recent events should come first.

### 118. Same-date raw vs derived event rows

**Q:** When a raw event and a derived event happen on the same date, should they be merged into one row or shown separately?

**A:** Show them separately.

Reason:
- this keeps source evidence and system interpretation explicit

### 119. Post-interview compilation step

**Q:** After the final question, should the interview log first be checked for contradictions and then compiled into an updated HLD while preserving the interview log as the raw source?

**A:** Yes.

## Working interpretation from answers so far

- Two independent systems are required.
- Both can reuse the same factual market evidence inputs.
- Setup state and manual trade state should stay distinct.
- Early accumulation entry and later Phase D entry must both be supported.
- Daily uploads are manual and shared across both systems.
- External file validation is atomic.
- Local delivery analysis is optional-but-visible, not a run blocker.
- Upload is a first-class evidence-capture and evidence-exploration workflow, not an automatic trigger for system evaluation.
- Evaluation should stop on unexpected Kite candle-history failure and surface the problem instead of skipping or hiding it.
- Run-time analysis is a stepwise, inspectable workflow with independently runnable stages rather than a single black-box execution.
- Day-based thresholds and windows should be configurable through a master JSON config.
- Same-day reruns should replace the visible daily result rather than accumulating multiple user-facing snapshots for the same day.
- Step-level visibility and validation are core product requirements because they enable bug detection, business-logic validation, and iterative product maturity.
- Forward should expose separate candidate sets rather than collapsing everything into one final watchlist.
- Forward validity is determined by shape validation; delivery contributes to conviction, not early rejection.
- High-conviction status is a hybrid of system evidence and human research judgment.
- Position tracking may be broker-synced from Kite holdings/GTT rather than manually entered, while still remaining execution-observational rather than auto-trading.
- Forward cases must remain visible even if not bought immediately, because capital constraints and later Phase D entries are normal.
- A later accumulation after an older completed holding/trade should create a new case, not reopen the old one.
- Upload-side stock views should expose both raw evidence and derived linkages so data flow and business logic can be validated together.
- Exit detection should be automated from Kite history rather than relying on manual confirmation by default.
- Upload-side evidence browsing should support both document-first and stock-first navigation paths.
- Every UI should support CSV export.
- Run workflows should support explicit date scopes such as today, last 30 days, or full available period.
- Upload-side is for evidence capture and linkage validation; run-side is for execution and evaluated outputs.
- Backward also needs separate visible step outputs for validation.
- For a given date, reruns replace the existing result rather than creating multiple same-date results.
- Run UIs need custom date-range support, with configurable defaults and current-date execution always allowed.
- Historical run outputs should be inspectable day by day, including date counts and stock-level appearance tracing.
- HLD should stay simple and revisable later during LLD/implementation; do not over-engineer for a single-user tool.
- Forward high-conviction ordering should be simple and system-driven: flat accumulations first, then longer bases, then stronger delivery/LVQ support, without building a complex ranking score.
- Forward should incorporate Groww Volume Shocker in its later Phase D conviction flow.
- Forward and Backward remain separate systems even when the same stock appears in both.
- Forward high-conviction cases should remain visible unless a later dedicated downgrade workflow is introduced.
- Backward succeeds only when the current move can be tied back to an accumulation period, even if that accumulation is not the strongest form.
- Forward late progression should be broken into multiple visible stages such as ignition, Phase D, volume shocker, and resistance-crossing/bull-run transition rather than one collapsed `Phase D started` step.
- Conviction-building steps themselves should remain visible separately, including accumulation, LVQ/footprint behavior, dry-up/volume behavior, delivery abnormality, and high-volume confirmation.
- A dedicated Wyckoff breakout/resistance-crossing stage is required to show when the stock may be entering the obvious bull-run zone.
- Supporting evidence such as LVQ, dry-up, delivery abnormality, and high-volume confirmation are not rigid ordered stages; they can appear before or during Phase D.
- Groww Volume Shocker is a flexible later-stage confirmation signal, not a fixed single-step gate.
- Breakout visibility must persist across the Forward flow, including resistance price, current distance, and breakout status.
- Forward should present a small set of main stages plus supporting evidence badges, rather than treating every signal as an equal stage.
- Breakout resistance should be derived from Kotlin candle-structure logic.
- Backward should expose the same accumulation descriptors and later-stage context as Forward, not just a binary success/fail label.
- Forward main stages are currently: raw accumulation, valid accumulation, high conviction, ignition seen, Phase D seen, breakout near, breakout done.
- Required HLD-level supporting evidence badges are LVQ/LV100/LVY, volume dry-up, delivery abnormal, high volume, and Groww shocker.
- Backward should reuse the same field set/UI pattern where practical while remaining a separate system.
- `breakout near` should use a configurable distance-below-resistance rule, currently set to 6%.
- `breakout done` should use daily close above resistance, while intraday failed-break attempts remain visible as evidence.
- Backward HLD buckets are raw phase d, gated phase d, converged phase d, and accumulation-backed candidates.
- Forward high-conviction ordering should be auditable by showing the reason a stock got priority.
- Forward `ignition seen` can be triggered by Chartink ignition or delivery shock.
- Accepted Phase D evidence sources are Chartink ignition, Chartink momentum, Groww volume shocker, and local delivery anomaly.
- High-conviction views should show all matching priority reasons, not just the top one.
- Validation reasoning should be visible across all pipeline steps, including rejection reasons.
- Historical results must be rerunnable because validation and backtesting are core maturity workflows.
- Backward can reuse breakout-context fields too, though this is lower priority than in Forward.
- Historical accumulation views should show all detected chains by date, not only the latest chain.
- The product has two clear usage modes: live/today-focused operation and historical validation/backtesting.
- Backward date-range summaries should show both counts by date and source breakdown.
- Main screens should default to today's view in live mode while still allowing wider date scopes.
- Upload-side document views should show per-date counts and per-stock repeat appearances.
- Rerun-and-replace is sufficient for HLD; explicit run-to-run comparison is not required now.
- Uploaded file structure must be valid across all rows, though raw uploaded data should still be visible for inspection.
- Supporting evidence badges should expose their dates/raw occurrence context.
- Run-step execution depends on prior-step outputs; steps are visible separately but not fully independent of upstream data.
- Missing upstream run dependencies should surface as explicit errors in the UI.
- High-conviction ordering can remain manually sorted for now.
- Upload-side raw inspection only needs table-level visibility at HLD stage.
- Historical step outputs only need to be visible for the currently selected date range.
- Post-breakout handling remains intentionally deferred to later design/implementation.
- Manual research should stay lightweight: simple notes plus manual quality tags like A+/A/B/C.
- Research quality tags are fully manual.
- Forward research notes/tags should remain attached to the same case as it progresses through later stages.
- Manual research tagging applies only to the Forward system.
- Stocks can remain visible in multiple Forward stage lists at once.
- Stocks can remain visible in multiple Backward step outputs at once.
- Upload/document counts should be scoped only to the selected date range.
- Earlier and later stages should remain simultaneously visible because their evidence dates can differ.
- Active date range should always be clearly shown in the UI for clarity in exports and screenshots.
- Each Forward stage should show its own explicit stage date.
- Each Backward step output should show its own explicit step/source date.
- CSV exports should include date-range context, stage/step dates, and source names.
- Forward stage dates should use the latest matching date as the primary displayed date, while prior hits remain historically inspectable.
- Backward should show separate dates per Phase D source plus the convergence result, not one collapsed combined date.
- Date-range views should not render explicit empty/no-result dates.
- Supporting evidence badges should expose latest date, and repeating signals should also expose count, with history still available for conviction-building.
- Backward convergence should explicitly list the contributing source dates inside the configured window.
- Upload-side document views should show repeat count plus latest appearance date for each stock.
- Backward output labels should be human-meaningful and self-explanatory.
- Forward rejected accumulation candidates should remain auditable through explicit rejection reasons.
- Upload-side stock detail should use a merged chronological timeline across sources.
- Backward HLD bucket names are: raw phase d signals, validated phase d signals, converged phase d signals, accumulation-backed phase d candidates.
- Merged stock timelines should show both source name and source-specific date per event.
- Accepted Forward accumulation candidates should also show explicit acceptance-reason summaries.
- High-conviction Forward entries should show explicit reason summaries too.
- Forward breakout-related list views should show the exact Kotlin-derived resistance value.
- Backward final candidate lists should show a compact but meaningful summary directly in list view.
- Later Forward stage lists should retain key accumulation context like start date and length.
- Backward summaries should show Phase D source badges.
- Stock timelines should include both raw evidence events and system-derived stage events.
- Stock timelines should default to newest-first ordering.
- Raw and derived events on the same date should remain separate rows.
- After the interview closes, the log should be checked for contradictions before compiling the updated HLD.

## Deferred to LLD / Implementation

- Whether any separate post-breakout workflow is needed beyond the current Forward flow

## Post-HLD Review Clarifications

### 120. Upload workflow boundary

**Q:** During HLD review, should the upload workflow stop at file upload, data sanity, and linkage visibility rather than doing Forward-style derived interpretation?

**A:** Yes.

The upload workflow should stay narrow:
- file upload
- data sanity
- linkage of data

It should not generate Forward-style interpretation such as possible accumulation chains as part of the upload workflow itself.

### 121. Forward raw accumulation boundary

**Q:** During HLD review, should `raw accumulation` mean only accumulation source hit plus chain found, with no extra early filtering?

**A:** Yes. That is enough.

### 122. Forward high-conviction computation

**Q:** During HLD review, should `high conviction` remain in the HLD as a real system-derived stage?

**A:** Yes.

The user clarified that high conviction should be based on system evidence such as:
- flat accumulation
- accumulation length
- how many times the low was hit
- delivery footprint
- volume dryness

### 123. Timeline/date semantics deferral

**Q:** During HLD review, should the remaining timeline/date-history semantics be finalized now?

**A:** No strong reasoning yet. These should be answered during implementation and validation.

Working interpretation:
- keep the current HLD direction
- refine the exact timeline/date/history behavior during module-level LLD and implementation
- use real validation feedback rather than forcing premature detail
