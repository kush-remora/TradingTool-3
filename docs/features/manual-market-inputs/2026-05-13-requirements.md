# Requirement: Manual Market Event Inputs

## Problem Statement
We want a simple and reliable way to use NSE market-event data inside the tool without depending on brittle NSE scraping or paid exchange integration too early.

The key workflows are:
- daily review of bulk/block deal activity,
- tracking IPO lock-in expiry supply events,
- daily review of 52-week-low stocks with quick quality filters.

The first version should optimize for reliability and clarity, not automation.

## Goal
Create a manual-input feature set where Kush can upload daily files and quickly answer:
- did any watchlist stock have meaningful bulk/block activity today,
- which upcoming lock-in expiries are worth researching because unlocked supply is large,
- which 52-week-low stocks are in the watchlist or still fundamentally strong.

## Product Decision
Version 1 is `manual upload first`.

Reason:
- NSE blocks or rate-limits normal automated access.
- Manual upload is more stable and faster to ship.
- The workflows are daily and low-volume, so manual input is acceptable.

Future automation can be explored later, but it should not be a dependency for V1.

## User Story
As a trader, I want to upload daily market files and immediately see which stocks from my watchlist or research universe need attention, so I can focus on a small number of relevant names instead of manually scanning raw files.

## Scope
This requirement covers three screens or three tabs within one market-input screen:
- `Bulk/Block Deals`
- `Lock-in Expiry Tracker`
- `52-Week Low Qualifier`

## Screen 1: Bulk/Block Deals

### Purpose
Use a daily NSE file to detect meaningful deal activity, especially in watchlist stocks, and highlight whether the activity may matter.

### Input
- Manual upload of the daily NSE bulk-deal or block-deal Excel/CSV file.

### Core Rules
- Flag any row where the stock is already in our watchlist.
- Show whether the deal is `BUY` side or `SELL` side.
- Keep the important reference values visible:
  - deal price
  - quantity
  - total value
  - buyer name
  - seller name
  - trade date
- Treat bulk-deal threshold as `more than 0.5%` of company equity when that context is available from the source/exchange definition.

### What the Screen Should Answer
- Did any watchlist stock have a meaningful deal today?
- Was the deal near current market price or far away?
- Was it institutional absorption or institutional exit?
- Is the deal price a useful reference level for later review?

### Suggested Output Sections
- `Watchlist hits`
- `Non-watchlist but notable deals`
- `Potential support-reference deals`
- `Potential exit-warning deals`

### Initial Heuristics
- More interesting:
  - buyer is a known mutual fund, institution, or large fund,
  - deal value is large,
  - stock is near the deal price,
  - action is on the buy side.
- Less interesting:
  - tiny value,
  - unknown participant with no clear signal value,
  - stock already moved far away from deal price.

### Notes
- The deal price is a `reference level`, not an automatic buy price.
- This screen is for signal surfacing, not direct trade execution.

## Screen 2: Lock-in Expiry Tracker

### Purpose
Track IPO or recently listed stocks where lock-in expiry may release meaningful supply into the market.

### Input
- Manual upload file or maintained sheet containing:
  - stock symbol
  - company name
  - listing date
  - lock-in expiry date
  - shares unlocking
  - total outstanding shares or enough data to compute unlock percentage
  - holder type if available

### Core Rules
- Compute or display:
  - unlock date
  - unlocked shares
  - unlock as `% of total equity`
- Rank events by size of unlock.
- Highlight upcoming events in a usable review window, for example:
  - next 7 days
  - next 30 days
- Allow Kush to manually judge whether the unlocked quantity is large enough to matter.

### What the Screen Should Answer
- Which lock-in expiries are approaching?
- How large is the potential supply event?
- Is this large enough to create pressure or enable institutional absorption?
- Should this stock move into active research before expiry?

### Suggested Output Sections
- `Upcoming major unlocks`
- `Upcoming medium unlocks`
- `Low-impact unlocks`
- `Research candidates`

### Initial Heuristics
- More important:
  - large absolute quantity,
  - large unlock percentage of equity,
  - stock has low free float,
  - stock is recently listed and still sentiment-driven.
- Less important:
  - very small unlock percentage,
  - already highly liquid and mature post-listing behavior,
  - no meaningful supply change.

### Connection to Bulk/Block Deals
- This screen should work with the bulk/block deal screen as a combined thesis:
  - lock-in expiry creates supply,
  - strong institutional buying near/after that event can matter,
  - deal price may become a useful review level.

## Screen 3: 52-Week Low Qualifier

### Purpose
Use a daily 52-week-low stock CSV to find names that deserve review, especially if they are already in the watchlist or still fundamentally strong.

### Input
- Manual upload of daily `52-week low` CSV.

### Core Rules
- Match uploaded names against the current watchlist.
- Show all watchlist matches first.
- For each stock, add a simple qualification layer instead of treating the low itself as a signal.

### Qualification Questions
- Is the business still fundamentally strong?
- Did the stock fall because of sentiment or because the business broke?
- Is debt manageable?
- Are earnings, margins, or cash flow still acceptable?
- Is valuation becoming attractive versus its own history or peer set?

### Output Classification
- `Watchlist stocks at 52-week low`
- `Strong fundamentals, worth review`
- `Weak fundamentals, avoid`
- `Needs manual review`

### Minimum Tags/Reasons
Each qualified stock should show short reason tags such as:
- `watchlist`
- `strong balance sheet`
- `earnings stable`
- `low debt`
- `cash flow okay`
- `sentiment fall`
- `business risk`
- `results weak`

### Notes
- A 52-week low is only a trigger for attention.
- It is not a buy reason by itself.

## Shared Requirements Across All Three Screens

### Upload Model
- User uploads files manually.
- System parses and validates file columns.
- System stores the uploaded run with a date stamp.
- System should show clear parse failures and missing-column errors.

### Watchlist Integration
- Any stock already present in the watchlist should be highlighted clearly.
- Watchlist overlap is a first-class filter across all three workflows.

### Review Output
- Results should be easy to scan in a compact table.
- Each row should include a short `why it matters` field.
- The tool should prefer simple tags and reason strings over long explanations.

### Filtering
- Filter by:
  - watchlist only,
  - all uploaded stocks,
  - high-priority only,
  - date,
  - event type.

### Persistence
- Keep upload history so the same file does not need to be reprocessed manually during the same review cycle.
- Preserve final user notes if later added.

## Non-Goals for V1
- No direct NSE API integration.
- No browser scraping dependency.
- No automated trade signal generation.
- No automatic “best price to buy” conclusion.
- No complex scoring model in the first release.

## Future Scope
- Semi-automated browser download flow if stable.
- Alternative vendor/API ingestion if cost and reliability make sense.
- Cross-linking between:
  - lock-in expiry event,
  - bulk/block deal event,
  - watchlist thesis,
  - fundamentals review.
- Lightweight ranking score after enough real usage.

## Acceptance Criteria
1. Kush can manually upload a daily bulk/block deal file and immediately see watchlist matches plus basic deal context.
2. Kush can maintain or upload lock-in expiry data and sort upcoming events by unlock size and unlock percentage.
3. Kush can manually upload a daily 52-week-low CSV and immediately see watchlist matches and qualification buckets.
4. All three workflows work without NSE API integration.
5. The UI emphasizes short reasons and fast review, not heavy analytics.

## Technical Considerations
- Prefer extending existing watchlist and screen patterns rather than building a new subsystem.
- Keep ingestion logic simple and file-driven.
- Data model should store uploaded rows with `source type`, `upload date`, and normalized symbol mapping.
- Symbol normalization will matter because uploaded files may use slightly different naming conventions.

## Open Questions for Later
- Should all three workflows live under one `Market Inputs` page or separate menu items?
- Do we want to persist manual review notes in V1 or only parsed rows?
- What is the minimum fundamentals data source for the 52-week-low qualifier:
  - existing internal fundamentals,
  - uploaded annotations,
  - or a simple manual verdict field first?

## Rough Complexity Estimate
- Requirements clarity: complete enough for implementation planning.
- V1 implementation complexity: `medium`.
- Main risk: file-format variability, not business logic complexity.
