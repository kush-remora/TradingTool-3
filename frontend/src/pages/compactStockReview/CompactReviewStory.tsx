import { Button, Input } from "antd";
import { useState } from "react";
import type { StockNote } from "../../types";
import type { CompactDailyRow } from "./compactStockReview";
import { formatPrice, formatQuantity, formatSignedPercent } from "./compactStockReview";

const { TextArea } = Input;

interface CompactReviewStoryProps {
  days: CompactDailyRow[];
  notes: StockNote[];
  notesLoading: boolean;
  notesError: string | null;
  onAddNote: (note: string) => Promise<boolean>;
}

const istDate = (createdAt: string): string =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date(createdAt));

const formatNoteDate = (date: string): string =>
  new Intl.DateTimeFormat("en-IN", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    timeZone: "UTC",
  }).format(new Date(`${date}T00:00:00Z`));

const formatNoteTime = (createdAt: string): string =>
  new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "Asia/Kolkata",
  }).format(new Date(createdAt));

export function CompactReviewStory({ days, notes, notesLoading, notesError, onAddNote }: CompactReviewStoryProps) {
  const [isComposerOpen, setIsComposerOpen] = useState(false);
  const [noteText, setNoteText] = useState("");
  const [saving, setSaving] = useState(false);
  const dayByDate = new Map(days.map((day) => [day.date, day]));

  const saveNote = async (): Promise<void> => {
    if (!noteText.trim()) return;
    setSaving(true);
    const didSave = await onAddNote(noteText);
    setSaving(false);
    if (!didSave) return;
    setNoteText("");
    setIsComposerOpen(false);
  };

  return (
    <aside className="compact-review-story" aria-label="Observation log">
      <div className="compact-review-section-heading compact-observation-heading">
        <strong>Observation log</strong>
        <Button type="link" size="small" onClick={() => setIsComposerOpen((open) => !open)}>
          {isComposerOpen ? "Close" : "Take note"}
        </Button>
      </div>

      {isComposerOpen && (
        <div className="compact-review-note-block compact-observation-composer">
          <TextArea
            aria-label="Today's observation"
            value={noteText}
            onChange={(event) => setNoteText(event.target.value)}
            placeholder="What do you see in today's data?"
            autoSize={{ minRows: 2, maxRows: 4 }}
          />
          <div className="compact-review-note-actions">
            <small />
            <Button type="link" size="small" loading={saving} disabled={!noteText.trim()} onClick={() => void saveNote()}>
              Send
            </Button>
          </div>
          {notesError && <small className="compact-negative">{notesError}</small>}
        </div>
      )}

      {notesLoading && <p className="compact-muted compact-observation-empty">Loading notes…</p>}
      {!notesLoading && notes.length === 0 && <p className="compact-muted compact-observation-empty">No observations yet. Take the first note.</p>}

      <div className="compact-observation-list">
        {notes.map((note) => {
          const noteDate = istDate(note.createdAt);
          const day = dayByDate.get(noteDate);
          return (
            <article className="compact-observation-note" key={note.id}>
              <div className="compact-observation-note-header">
                <strong>{formatNoteDate(noteDate)}</strong>
                <small>{formatNoteTime(note.createdAt)}</small>
              </div>
              {day && <ObservationEvidence day={day} />}
              <p>{note.notes}</p>
            </article>
          );
        })}
      </div>
    </aside>
  );
}

function ObservationEvidence({ day }: { day: CompactDailyRow }) {
  return (
    <table className="compact-observation-evidence">
      <tbody>
        <tr>
          <th>O / H / L / C</th>
          <td>{formatPrice(day.open)} / {formatPrice(day.high)} / {formatPrice(day.low)} / {formatPrice(day.close)}</td>
        </tr>
        <tr>
          <th>Day %</th>
          <td className={day.daily_change_pct == null ? "compact-muted" : day.daily_change_pct < 0 ? "compact-negative" : "compact-positive"}>{formatSignedPercent(day.daily_change_pct, 1)}</td>
          <th>Volume</th>
          <td>{formatQuantity(day.volume)}{day.volumeVsPrior10dPct == null ? "" : ` · ${day.volumeVsPrior10dPct.toFixed(0)}% / 10D`}</td>
          <th>Delivery</th>
          <td>{day.deliveryPct == null ? "—" : `${day.deliveryPct.toFixed(1)}%`}</td>
        </tr>
      </tbody>
    </table>
  );
}
