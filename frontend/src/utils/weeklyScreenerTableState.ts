export type ColumnPin = "left" | "right" | "none";

export interface WeeklyScreenerColumnConfig {
  key: string;
  visible: boolean;
  pin: ColumnPin;
}

export interface WeeklyScreenerSortState {
  columnKey: string | null;
  order: "ascend" | "descend" | null;
}

export function moveColumn(
  columns: WeeklyScreenerColumnConfig[],
  key: string,
  direction: "up" | "down",
): WeeklyScreenerColumnConfig[] {
  const index = columns.findIndex((column) => column.key === key);
  if (index < 0) return columns;
  const targetIndex = direction === "up" ? index - 1 : index + 1;
  if (targetIndex < 0 || targetIndex >= columns.length) return columns;

  const clone = [...columns];
  const [picked] = clone.splice(index, 1);
  clone.splice(targetIndex, 0, picked);
  return clone;
}

export function updateColumnVisibility(
  columns: WeeklyScreenerColumnConfig[],
  key: string,
  visible: boolean,
): WeeklyScreenerColumnConfig[] {
  return columns.map((column) => (column.key === key ? { ...column, visible } : column));
}

export function updateColumnPin(
  columns: WeeklyScreenerColumnConfig[],
  key: string,
  pin: ColumnPin,
): WeeklyScreenerColumnConfig[] {
  return columns.map((column) => (column.key === key ? { ...column, pin } : column));
}

export function parseJsonSafely<T>(raw: string | null): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}
