---
name: TradingTool UI Philosophy
description: Design guidelines for creating and modifying frontend components to maintain a compact, high-density, and clean interface inspired by Groww.
---

# TradingTool-3 UI Design Philosophy

When designing or updating the UI for TradingTool-3, you MUST draw heavy inspiration from platforms like Groww, which balance the need for detailed financial data with a clean, uncluttered, and approachable aesthetic.

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

### Implementation Checklist
- [ ] Are Ant Design Tables using `size="small"`?
- [ ] Have you minimized padding and margin for high data density?
- [ ] Are profit/loss colors properly distinct from the background?
- [ ] Are fonts small enough (11px-13px) to prevent vertical scrolling on large data sets?
