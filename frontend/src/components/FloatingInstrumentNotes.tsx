import { DeleteOutlined, FileTextOutlined, SendOutlined } from "@ant-design/icons";
import { Button, Input, Popover, Space, Typography } from "antd";
import { useState } from "react";
import type { StockNote } from "../types";

const { Text } = Typography;
const { TextArea } = Input;

interface FloatingInstrumentNotesProps {
  notes: StockNote[];
  loading: boolean;
  error: string | null;
  onAddNote: (notes: string) => Promise<boolean>;
  onRemoveNote: (id: number) => Promise<boolean>;
}

export function FloatingInstrumentNotes({ notes, loading, error, onAddNote, onRemoveNote }: FloatingInstrumentNotesProps) {
  const [noteText, setNoteText] = useState("");

  const addNote = async (): Promise<void> => {
    if (!noteText.trim()) return;
    if (await onAddNote(noteText)) setNoteText("");
  };

  const removeNote = async (id: number): Promise<void> => {
    await onRemoveNote(id);
  };

  return (
    <div data-testid="floating-instrument-notes" style={{ position: "fixed", right: "min(292px, calc(100vw - 40px))", bottom: 20, zIndex: 1000 }}>
      <Popover
        trigger="click"
        placement="topRight"
        title="Research notes"
        content={(
          <Space orientation="vertical" size={8} style={{ width: 260 }}>
            <TextArea
              aria-label="New note"
              value={noteText}
              onChange={(event) => setNoteText(event.target.value)}
              placeholder="Write a note"
              autoSize={{ minRows: 2, maxRows: 4 }}
            />
            <Button aria-label="Save note" size="small" type="primary" icon={<SendOutlined />} onClick={() => void addNote()} disabled={!noteText.trim()}>
              Save
            </Button>
            {error && <Text type="danger">{error}</Text>}
            {loading ? <Text type="secondary">Loading notes…</Text> : notes.length === 0 ? <Text type="secondary">No saved notes.</Text> : (
              <div style={{ maxHeight: 180, overflowY: "auto" }}>
                <Space orientation="vertical" size={6} style={{ width: "100%" }}>
                  {notes.map((note) => (
                    <div key={note.id} style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
                      <Text style={{ flex: 1, fontSize: 12 }}>{note.notes}</Text>
                      <Button
                        aria-label={`Delete note ${note.id}`}
                        type="text"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => void removeNote(note.id)}
                      />
                    </div>
                  ))}
                </Space>
              </div>
            )}
          </Space>
        )}
      >
        <Button aria-label="Open research notes" type="primary" shape="circle" icon={<FileTextOutlined />} />
      </Popover>
    </div>
  );
}
