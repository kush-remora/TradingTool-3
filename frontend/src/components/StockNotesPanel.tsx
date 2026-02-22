import { CloseOutlined, DeleteOutlined, SendOutlined } from "@ant-design/icons";
import { Button, Input, Rate, Spin, Tag, Tooltip, Typography } from "antd";
import { useState } from "react";
import { useStockNotes } from "../hooks/useStockNotes";
import type { Stock, Tag as TagType } from "../types";

interface Props {
  stock: Stock;
  tags: TagType[];
  onClose: () => void;
  onUpdateDescription: (description: string) => Promise<void>;
  onUpdatePriority: (priority: number) => Promise<void>;
}

export function StockNotesPanel({ stock, tags, onClose, onUpdateDescription, onUpdatePriority }: Props) {
  const { notes, loading, addNote, removeNote } = useStockNotes(stock.id);
  const [noteInput, setNoteInput] = useState("");
  const [sendingNote, setSendingNote] = useState(false);
  const [editingDescription, setEditingDescription] = useState(false);
  const [descriptionDraft, setDescriptionDraft] = useState(stock.description ?? "");
  const [savingDescription, setSavingDescription] = useState(false);

  const handleSendNote = async () => {
    const content = noteInput.trim();
    if (!content) return;
    setSendingNote(true);
    try {
      await addNote(content);
      setNoteInput("");
    } finally {
      setSendingNote(false);
    }
  };

  const handleSaveDescription = async () => {
    setSavingDescription(true);
    try {
      await onUpdateDescription(descriptionDraft);
      setEditingDescription(false);
    } finally {
      setSavingDescription(false);
    }
  };

  const formatTime = (iso: string) =>
    new Date(iso).toLocaleDateString("en-IN", {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });

  return (
    <div
      style={{
        position: "fixed",
        bottom: 80,
        right: 24,
        width: 360,
        height: 520,
        background: "#fff",
        border: "1px solid #e8e8e8",
        borderRadius: 12,
        boxShadow: "0 8px 32px rgba(0,0,0,0.12)",
        display: "flex",
        flexDirection: "column",
        zIndex: 1000,
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: "12px 16px",
          borderBottom: "1px solid #f0f0f0",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          background: "#fafafa",
          borderRadius: "12px 12px 0 0",
        }}
      >
        <div>
          <Typography.Text strong style={{ fontSize: 14 }}>
            {stock.symbol}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11, marginLeft: 6 }}>
            {stock.exchange}
          </Typography.Text>
          <div>
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {stock.company_name}
            </Typography.Text>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Rate
            count={5}
            value={stock.priority ?? 0}
            onChange={(val) => void onUpdatePriority(val)}
            style={{ fontSize: 12 }}
          />
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined />}
            onClick={onClose}
          />
        </div>
      </div>

      {/* Tags */}
      {tags.length > 0 && (
        <div style={{ padding: "8px 16px", borderBottom: "1px solid #f0f0f0", display: "flex", flexWrap: "wrap", gap: 4 }}>
          {tags.map((tag) => (
            <Tag key={tag.id} color="blue" style={{ margin: 0, fontSize: 11 }}>
              {tag.name}
            </Tag>
          ))}
        </div>
      )}

      {/* Description */}
      <div style={{ padding: "10px 16px", borderBottom: "1px solid #f0f0f0" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 4 }}>
          <Typography.Text style={{ fontSize: 11, color: "#888", fontWeight: 600 }}>MOAT / THESIS</Typography.Text>
          {!editingDescription && (
            <Button type="link" size="small" style={{ fontSize: 11, padding: 0, height: "auto" }} onClick={() => setEditingDescription(true)}>
              Edit
            </Button>
          )}
        </div>
        {editingDescription ? (
          <div>
            <Input.TextArea
              autoFocus
              rows={3}
              value={descriptionDraft}
              onChange={(e) => setDescriptionDraft(e.target.value)}
              style={{ fontSize: 12, resize: "none" }}
            />
            <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
              <Button size="small" type="primary" loading={savingDescription} onClick={() => void handleSaveDescription()}>
                Save
              </Button>
              <Button size="small" onClick={() => { setEditingDescription(false); setDescriptionDraft(stock.description ?? ""); }}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <Typography.Text style={{ fontSize: 12, color: stock.description ? "#333" : "#bbb" }}>
            {stock.description || "No thesis yet — click Edit to add one."}
          </Typography.Text>
        )}
      </div>

      {/* Notes timeline */}
      <div style={{ flex: 1, overflowY: "auto", padding: "8px 16px" }}>
        {loading ? (
          <div style={{ textAlign: "center", paddingTop: 16 }}>
            <Spin size="small" />
          </div>
        ) : notes.length === 0 ? (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            No journal entries yet.
          </Typography.Text>
        ) : (
          notes.map((note) => (
            <div
              key={note.id}
              style={{
                marginBottom: 10,
                background: "#f6f8fa",
                borderRadius: 8,
                padding: "8px 10px",
                position: "relative",
              }}
            >
              <Typography.Text style={{ fontSize: 12, display: "block" }}>{note.content}</Typography.Text>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 4 }}>
                <Typography.Text type="secondary" style={{ fontSize: 10 }}>
                  {formatTime(note.created_at)}
                </Typography.Text>
                <Tooltip title="Delete note">
                  <Button
                    type="text"
                    size="small"
                    icon={<DeleteOutlined style={{ fontSize: 10, color: "#ccc" }} />}
                    onClick={() => void removeNote(note.id)}
                    style={{ padding: "0 2px", height: 16 }}
                  />
                </Tooltip>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Input */}
      <div style={{ padding: "10px 12px", borderTop: "1px solid #f0f0f0", display: "flex", gap: 8 }}>
        <Input
          placeholder="Add a journal entry..."
          value={noteInput}
          onChange={(e) => setNoteInput(e.target.value)}
          onPressEnter={() => void handleSendNote()}
          size="small"
          style={{ fontSize: 12 }}
        />
        <Button
          type="primary"
          size="small"
          icon={<SendOutlined />}
          loading={sendingNote}
          onClick={() => void handleSendNote()}
          disabled={!noteInput.trim()}
        />
      </div>
    </div>
  );
}
