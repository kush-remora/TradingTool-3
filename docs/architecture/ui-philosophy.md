# TradingTool-3 UI Design Philosophy

For all Remora-related screens, this file should be read together with [docs/strategies/remora-philosophy.md](/Users/kushbhardwaj/Documents/github/TradingTool-3/docs/strategies/remora-philosophy.md). The UI is not just presenting market data; it should express the institutional-footprint philosophy behind the strategy.

## The "Groww" Inspiration: Simple, Compact, Data-Dense

When designing or updating the UI for TradingTool-3, we draw heavy inspiration from platforms like Groww, which balance the need for detailed financial data with a clean, uncluttered, and approachable aesthetic. We want our trading tool to feel more like a streamlined e-commerce platform than a disjointed terminal wrapper.

### Key Principles

1. **High Information Density (Compact Design)**
   - Use **small fonts** and tight padding across tables, lists, and form inputs.
   - Screen real estate is highly valuable; traders need to see multiple watchlists, graphs, and trade journals without excessive scrolling.
   - Utilize specific component sizing attributes (e.g., `size="small"` in Ant Design) to keep rows and columns lean.

2. **Cleanliness & Simplicity**
   - Eliminate unnecessary borders, heavy shadows, or decorative elements that don't serve a functional purpose.
   - Employ a calm color palette that allows profit (green) and loss (red) indicators to stand out without competing with the background.
   - Navigation should be direct. Tabs and menus must be self-explanatory and always visible.

3. **Responsive Typography**
   - While prioritizing small fonts for data density, ensure that the typography remains readable. 
   - Utilize a clear sans-serif font (like Inter or Roboto) that reads well at 11px–13px sizes.

4. **Action-Oriented Interface**
   - Buttons and actionable items (like "+ Add", "Buy", "Sell") should have primary emphasis.
   - Modals and Drawers should slide in without jarring context switches, allowing users to enter trade details while keeping an eye on the background data.

By adhering to these principles, TradingTool-3 will provide an expert-level trading experience in a visually accessible package.

## Remora-Specific UI Rule

When building Remora UI, prioritize clarity around:

1. footprint detection,
2. breakout confirmation,
3. readiness to act,
4. and why a stock is not yet actionable.

If a screen shows numbers without helping the user understand those four things, the screen is incomplete.
