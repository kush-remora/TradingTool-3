# Trading UI Development — Groww-Inspired Patterns

**Inspired by:** [Groww](https://groww.in/) trading platform
**Stack:** React (functional components) + Ant Design + lightweight-charts
**Project:** TradingTool personal trading dashboard

---

## Core Design Philosophy

Trading UIs must achieve **maximum information density with minimum cognitive load**. Users need to see multiple data streams simultaneously (prices, positions, watchlists, charts) without feeling overwhelmed.

**Guiding Principle:** *Simplicity in structure, density in content.*

---

## 1. Layout Patterns

### Multi-Panel Dashboard Layout
```
┌─────────────────────────────────────────┐
│      Header (Search, Navigation)        │
├──────────────────┬──────────────────────┤
│   Watchlist      │   Chart + Indicators │
│   (compact)      │   (primary focus)    │
├──────────────────┼──────────────────────┤
│  Orders/Positions│  Market Data Stream  │
│   (tight rows)   │  (real-time tickers) │
└──────────────────┴──────────────────────┘
```

**Implementation in Ant Design:**
- Use `<Layout.Content>` with `<Row>` and `<Col>` for grid layout
- Set `gutter` to small values (8–16px) for compact spacing
- Nest `<Card>` components for distinct sections
- Use `display: grid` for auto-responsive breakdowns on smaller screens

### Card-Based Sections
- Each functional area (watchlist, chart, orders) in its own `<Card>`
- Remove Card padding on trading data sections to maximize space
- Use Card `bordered={false}` for seamless column merging
- Titles in Cards use small, bold fonts (12–14px)

---

## 2. Typography & Spacing

### Font Sizes (Small & Tight)
| Element | Size | Weight | Usage |
|---------|------|--------|-------|
| Page Title | 18px | 600 | Dashboard header |
| Card Title | 13px | 600 | Section headers |
| Data Label | 11px | 500 | Column headers in tables |
| Data Value | 12px | 600 | Price, quantity, P&L |
| Meta Text | 10px | 400 | Timestamps, units |

**Rationale:** Small fonts force compact layouts; users zoom in when they need detail (browser zoom for desktop traders is normal).

### Spacing
- **Component margins:** 12–16px (not Ant's default 16–24px)
- **Card padding:** 12px (not default 24px)
- **Table row height:** 32–36px (not 48px default)
- **Watchlist item height:** 28–32px
- **Section gap:** 8–12px

```jsx
// Example: Tight watchlist
<Table
  dataSource={watchlist}
  pagination={false}
  size="small"
  rowKey="id"
  style={{ fontSize: '11px' }}
  columns={[
    { title: 'Symbol', dataIndex: 'symbol', width: '40%' },
    { title: 'Price', dataIndex: 'price', width: '30%' },
    { title: 'Change', dataIndex: 'change', width: '30%' },
  ]}
/>
```

---

## 3. Color System (from Groww Inspiration)

### Semantic Colors
```css
--color-entry: #00ff9d;    /* Buy signals, entry zones */
--color-stop: #ff3d5a;     /* Stop losses, sell signals */
--color-target: #ffb830;   /* Profit targets, resistance */
--color-hold: #4fa3ff;     /* Current positions, buy-and-hold */
--color-neutral: #9ca3af;  /* Neutral data, secondary text */
--color-bg-dark: #0f172a;  /* Trading dashboard background */
--color-chart-bg: #1a2332; /* Chart background */
```

### Application
- **Green (#00ff9d)** → positive changes, long entries
- **Red (#ff3d5a)** → negative changes, stops
- **Amber (#ffb830)** → warnings, targets, watch zones
- **Blue (#4fa3ff)** → holdings, neutral positions
- Grayscale for labels and secondary data

---

## 4. Real-Time Data Display

### Live Tickers (Watchlist)
```jsx
const WatchlistItem = ({ symbol, price, change, changePercent }) => (
  <Row justify="space-between" style={{ padding: '8px 12px', borderBottom: '1px solid #e5e7eb', fontSize: '11px' }}>
    <Col span={8}><strong>{symbol}</strong></Col>
    <Col span={8} style={{ textAlign: 'right', color: price > prevPrice ? '#00ff9d' : '#ff3d5a' }}>
      ₹{price.toFixed(2)}
    </Col>
    <Col span={8} style={{ textAlign: 'right', color: changePercent > 0 ? '#00ff9d' : '#ff3d5a' }}>
      {changePercent > 0 ? '+' : ''}{changePercent.toFixed(2)}%
    </Col>
  </Row>
);
```

**Patterns:**
- Update prices every 100–500ms (use React `useEffect` with `setInterval`)
- Highlight price changes with brief color flashes (animate opacity/background)
- Group by:
  - Gainers/Losers
  - Sector/Watchlist
  - Activity (most traded, highest volume)

### Position Panel
```jsx
const PositionSummary = ({ positions }) => (
  <Table
    columns={[
      { title: 'Stock', dataIndex: 'symbol', width: '25%' },
      { title: 'Qty', dataIndex: 'qty', width: '15%', align: 'right' },
      { title: 'Avg', dataIndex: 'avgPrice', width: '20%', align: 'right' },
      { title: 'LTP', dataIndex: 'ltp', width: '20%', align: 'right' },
      { title: 'P&L', dataIndex: 'pnl', width: '20%', align: 'right', render: (pnl) => (
        <span style={{ color: pnl > 0 ? '#00ff9d' : '#ff3d5a', fontWeight: 600 }}>
          {pnl > 0 ? '+' : ''}{pnl.toFixed(0)}
        </span>
      )},
    ]}
    dataSource={positions}
    size="small"
    pagination={false}
  />
);
```

---

## 5. Chart Integration (lightweight-charts)

### Layout
- **Chart size:** 60–70% of dashboard width (responsive)
- **Height:** 300–400px (leaving room for indicators below)
- **Margins:** Minimize padding around chart; let price bars touch edges

```jsx
import { ChartContainer } from 'lightweight-charts-react';

const PriceChart = ({ data, symbol }) => {
  const chartOptions = {
    width: 600,
    height: 350,
    layout: {
      backgroundColor: '#1a2332',
      textColor: '#d1d5db',
      fontSize: 11,
    },
    timeScale: {
      timeVisible: true,
      secondsVisible: false,
    },
  };

  return (
    <Card style={{ padding: '12px' }}>
      <ChartContainer options={chartOptions}>
        {/* Add candlestick series, volume series, indicators */}
      </ChartContainer>
    </Card>
  );
};
```

### Indicators (Compact)
- Display 2–3 key indicators (RSI, MACD, Bollinger Bands) **below** main chart
- Use separate `<AreaSeries>` for each indicator
- Small font sizes (10px) for indicator labels
- Highlight threshold crossovers (e.g., RSI > 70 = overbought) with subtle background color

---

## 6. Forms & Input (Order Entry)

### Compact Order Form
```jsx
const OrderForm = ({ symbol, currentPrice }) => (
  <Card style={{ padding: '12px' }}>
    <Form layout="vertical" size="small">
      <Form.Item label="Quantity" style={{ marginBottom: '8px' }}>
        <InputNumber min={1} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item label="Price" style={{ marginBottom: '8px' }}>
        <InputNumber step={0.05} defaultValue={currentPrice} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item label="Stop Loss" style={{ marginBottom: '8px' }}>
        <InputNumber step={0.05} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item label="Target" style={{ marginBottom: '8px' }}>
        <InputNumber step={0.05} style={{ width: '100%' }} />
      </Form.Item>
      <Button type="primary" block danger style={{ fontSize: '12px' }}>Buy</Button>
      <Button block style={{ marginTop: '4px', fontSize: '12px' }}>Sell</Button>
    </Form>
  </Card>
);
```

**Principles:**
- Stack fields vertically (no side-by-side to save horizontal space)
- Use `size="small"` on all Form components
- Reduce label font to 11px
- Input height: 28–32px (not default 40px)
- Button text: 12px
- Remove extra margins between fields (`marginBottom: '8px'`)

---

## 7. Information Hierarchy (Data Density)

### Top-Level Summary (Dashboard Header)
```jsx
const DashboardHeader = ({ portfolio }) => (
  <Row gutter={12} style={{ marginBottom: '12px', padding: '12px', backgroundColor: '#1a2332', borderRadius: '4px' }}>
    <Col>
      <div style={{ fontSize: '10px', color: '#9ca3af' }}>Portfolio Value</div>
      <div style={{ fontSize: '14px', fontWeight: 600 }}>${portfolio.totalValue.toFixed(0)}</div>
    </Col>
    <Col>
      <div style={{ fontSize: '10px', color: '#9ca3af' }}>Day P&L</div>
      <div style={{ fontSize: '14px', fontWeight: 600, color: portfolio.dayPnl > 0 ? '#00ff9d' : '#ff3d5a' }}>
        {portfolio.dayPnl > 0 ? '+' : ''}{portfolio.dayPnl.toFixed(0)}
      </div>
    </Col>
    <Col>
      <div style={{ fontSize: '10px', color: '#9ca3af' }}>Return %</div>
      <div style={{ fontSize: '14px', fontWeight: 600, color: portfolio.returnPct > 0 ? '#00ff9d' : '#ff3d5a' }}>
        {portfolio.returnPct > 0 ? '+' : ''}{portfolio.returnPct.toFixed(2)}%
      </div>
    </Col>
  </Row>
);
```

**Pattern:** Key metrics in a single horizontal row at the top; update every 1–2 seconds.

### Secondary Data (Tables)
- Show 6–8 rows at a time (scroll for more)
- Use monospace font for numbers (better alignment)
- Alternate row background colors subtly (every 2nd row, 2% darker)
- Show tooltips on hover for truncated values (symbol names, descriptions)

---

## 8. Responsive Breakdowns

### Desktop (> 1200px)
- 3-column: Watchlist | Chart | Orders
- All panels visible simultaneously

### Tablet (768–1200px)
- 2-column: Watchlist | Chart (stacked)
- Orders below as tabs or accordion

### Mobile (< 768px)
- Single column: Chart → Watchlist → Orders
- Horizontal scroll for wider tables
- Use Ant `<Tabs>` to switch between sections

```jsx
const ResponsiveDashboard = () => {
  const isMobile = useMediaQuery('(max-width: 768px)');

  return isMobile ? (
    <Tabs>
      <Tabs.TabPane tab="Chart" key="chart"><PriceChart /></Tabs.TabPane>
      <Tabs.TabPane tab="Watchlist" key="watchlist"><Watchlist /></Tabs.TabPane>
      <Tabs.TabPane tab="Orders" key="orders"><OrderHistory /></Tabs.TabPane>
    </Tabs>
  ) : (
    <Row gutter={12}>
      <Col span={6}><Watchlist /></Col>
      <Col span={12}><PriceChart /></Col>
      <Col span={6}><OrderPanel /></Col>
    </Row>
  );
};
```

---

## 9. Performance Tips (Real-Time Data)

1. **Memoization:** Use `React.memo()` for watchlist items to avoid re-rendering all rows on price update
2. **Virtual scrolling:** For watchlists > 100 items, use `<List virtualized={true} />`
3. **Debounced updates:** Batch price updates every 100–200ms instead of updating on every tick
4. **WebSocket over polling:** Use event streams (SSE/WebSocket) for live data; never use `setInterval` with API calls
5. **Chart optimization:** Limit candlesticks to last 100–500 bars; archive older data server-side

---

## 10. Component Checklist

When building a trading UI component, ensure:

- [ ] Font sizes are 10–14px (no padding waste)
- [ ] Card padding is 12px (not 24px)
- [ ] Row gaps are 8–12px
- [ ] Color coding applied (green/red for P&L, amber for targets)
- [ ] Real-time updates don't block UI (use async updates)
- [ ] Numbers right-aligned (monospace font for precision)
- [ ] Keyboard shortcuts for common actions (keyboard-accessible)
- [ ] No unnecessary whitespace (every pixel counts)
- [ ] Tooltips for truncated values
- [ ] Dark theme by default (trading dashboards are 24/7)

---

## Reference: Design Files

Refer to design mockups in `/designs/` for component layouts:
- `alpha10.jsx` — Alpha-10 strategy dashboard
- `momentum-screener.jsx` — Stock screening interface
- `weekend-investing.jsx` — Batch trade planner
- `netweb-improved-plan.html` — Swing trade plan (design system reference)

---

## Examples from TradingTool

### Watchlist Component Pattern
```jsx
import { Table, Card, Row, Col } from 'antd';

export const Watchlist = ({ stocks, onSelect }) => (
  <Card
    title="Watchlist"
    size="small"
    style={{ padding: '12px' }}
    bodyStyle={{ padding: '0px' }}
  >
    <Table
      columns={[
        { title: 'Symbol', dataIndex: 'symbol', width: '40%', render: (txt) => <strong>{txt}</strong> },
        { title: 'Price', dataIndex: 'price', width: '30%', align: 'right', render: (n) => n.toFixed(2) },
        { title: 'Chg %', dataIndex: 'changePercent', width: '30%', align: 'right',
          render: (pct) => <span style={{ color: pct > 0 ? '#00ff9d' : '#ff3d5a' }}>{pct > 0 ? '+' : ''}{pct.toFixed(2)}%</span> },
      ]}
      dataSource={stocks}
      size="small"
      pagination={false}
      onRow={(record) => ({ onClick: () => onSelect(record.symbol) })}
      style={{ fontSize: '11px', cursor: 'pointer' }}
    />
  </Card>
);
```

---

## When to Use This Skill

Use this guide when:
- Building dashboard layouts for trading strategies
- Designing watchlist or position displays
- Integrating charts into multi-panel layouts
- Styling forms for order entry
- Optimizing information density on small screens
- Applying color coding for trading signals
- Implementing real-time data updates
