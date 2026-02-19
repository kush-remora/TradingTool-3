---
name: trading-ui
description: Build compact, information-dense trading tool UIs using React, Ant Design, and lightweight-charts-react. Everything is small, tight, and maximally visible on one screen.
---

# Trading UI Skill

You are building a **trading tool**. Trading UIs are compact by design — traders need to see many data points simultaneously. Never waste space.

## Core Philosophy

**Small. Dense. Fast to scan.**
Every pixel counts. Use Ant Design's `size="small"` on everything. No large buttons, no padding-heavy cards, no big typography. Think Bloomberg terminal, not landing page.

## Stack

- **React** (functional components + hooks)
- **Ant Design** — always pass `size="small"` to all form controls, tables, buttons
- **lightweight-charts-react** — for price/time series charts

## Layout Rules

Use a tight grid. Prefer `Row`/`Col` with `gutter={[4,4]}`. Cards use `bodyStyle={{ padding: '6px 10px' }}`. No whitespace for decoration.

```jsx
<Card size="small" bodyStyle={{ padding: '6px 10px' }} title={<span style={{ fontSize: 11 }}>Panel Title</span>}>
```

## Typography Scale

| Use | Size |
|-----|------|
| Panel titles | 11px |
| Data labels | 11px, color: #888 |
| Data values | 12px, fontWeight: 500 |
| Important values (P&L, price) | 13px, fontWeight: 600 |

Never use default Ant Design heading components (h1–h4). Use `<span style={{ fontSize: ... }}>` directly.

## Ant Design Component Defaults

Always set these — never use default sizing:

```jsx
<Button size="small" />
<Input size="small" />
<Select size="small" />
<Table size="small" pagination={false} />
<Tabs size="small" />
<Tag style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }} />
```

## Charts (lightweight-charts-react)

Keep charts compact. Use fixed heights (120–200px for small panels, 300–400px for main chart). Configure tight grid lines and minimal margins:

```jsx
import { Chart, CandlestickSeries, LineSeries } from 'lightweight-charts-react';

<div style={{ height: 200 }}>
  <Chart
    layout={{ background: { color: '#0d0d0d' }, textColor: '#888' }}
    grid={{ vertLines: { color: '#1a1a1a' }, horzLines: { color: '#1a1a1a' } }}
    timeScale={{ borderColor: '#2a2a2a', timeVisible: true, secondsVisible: false }}
    rightPriceScale={{ borderColor: '#2a2a2a' }}
  >
    <CandlestickSeries data={data} />
  </Chart>
</div>
```

## Color Conventions

Use dark theme. Standard trading colors:

```js
const colors = {
  bg: '#0d0d0d',
  panel: '#141414',
  border: '#1f1f1f',
  up: '#26a69a',      // green/teal for gains
  down: '#ef5350',    // red for losses
  muted: '#555',
  text: '#ccc',
  label: '#888',
};
```

Apply inline: `<span style={{ color: value >= 0 ? colors.up : colors.down }}>`

## Table Pattern

Compact tables with no pagination, small font, tight rows:

```jsx
<Table
  size="small"
  dataSource={data}
  columns={columns}
  pagination={false}
  style={{ fontSize: 11 }}
  rowStyle={{ height: 24 }}
/>
```

Column widths should be explicit and minimal. Use `ellipsis: true` on text columns.

## Do Not

- No `Space` component with large gaps
- No `Typography.Title` or `Typography.Text`
- No default-sized anything
- No modals for things that fit inline
- No decorative dividers or extra margins