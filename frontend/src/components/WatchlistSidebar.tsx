import { useState } from "react";
import { Button, Input, Modal, Spin, Tooltip, Typography } from "antd";
import { DeleteOutlined, EditOutlined, PlusOutlined } from "@ant-design/icons";
import type { Watchlist } from "../types";

const C = {
  bg: "#0d0d0d",
  panel: "#141414",
  border: "#1f1f1f",
  active: "#1a2a1a",
  activeBorder: "#26a69a",
  text: "#ccc",
  label: "#888",
  danger: "#ef5350",
};

interface Props {
  watchlists: Watchlist[];
  selectedId: number | null;
  loading: boolean;
  onSelect: (id: number) => void;
  onCreate: (name: string) => Promise<void>;
  onRename: (id: number, name: string) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
}

export function WatchlistSidebar({
  watchlists,
  selectedId,
  loading,
  onSelect,
  onCreate,
  onRename,
  onDelete,
}: Props) {
  const [createOpen, setCreateOpen] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createBusy, setCreateBusy] = useState(false);

  const [renameId, setRenameId] = useState<number | null>(null);
  const [renameName, setRenameName] = useState("");
  const [renameBusy, setRenameBusy] = useState(false);

  const handleCreate = async () => {
    if (!createName.trim()) return;
    setCreateBusy(true);
    try {
      await onCreate(createName.trim());
      setCreateName("");
      setCreateOpen(false);
    } finally {
      setCreateBusy(false);
    }
  };

  const handleRename = async () => {
    if (renameId === null || !renameName.trim()) return;
    setRenameBusy(true);
    try {
      await onRename(renameId, renameName.trim());
      setRenameId(null);
    } finally {
      setRenameBusy(false);
    }
  };

  return (
    <div
      style={{
        width: 200,
        minWidth: 200,
        background: C.bg,
        borderRight: `1px solid ${C.border}`,
        display: "flex",
        flexDirection: "column",
        height: "100%",
        overflow: "hidden",
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: "8px 10px",
          borderBottom: `1px solid ${C.border}`,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <span style={{ fontSize: 11, color: C.label, fontWeight: 600, letterSpacing: 1 }}>
          WATCHLISTS
        </span>
        <Tooltip title="New watchlist" placement="right">
          <Button
            size="small"
            type="text"
            icon={<PlusOutlined style={{ fontSize: 11, color: C.label }} />}
            onClick={() => setCreateOpen(true)}
            style={{ padding: "0 4px", height: 20 }}
          />
        </Tooltip>
      </div>

      {/* List */}
      <div style={{ flex: 1, overflowY: "auto", padding: "4px 0" }}>
        {loading ? (
          <div style={{ padding: 16, textAlign: "center" }}>
            <Spin size="small" />
          </div>
        ) : watchlists.length === 0 ? (
          <Typography.Text style={{ fontSize: 11, color: C.label, padding: "12px 10px", display: "block" }}>
            No watchlists yet
          </Typography.Text>
        ) : (
          watchlists.map((w) => (
            <WatchlistItem
              key={w.id}
              watchlist={w}
              isActive={w.id === selectedId}
              onSelect={() => onSelect(w.id)}
              onEdit={() => {
                setRenameId(w.id);
                setRenameName(w.name);
              }}
              onDelete={() => void onDelete(w.id)}
            />
          ))
        )}
      </div>

      {/* Create Modal */}
      <Modal
        open={createOpen}
        title={<span style={{ fontSize: 12, color: C.text }}>New Watchlist</span>}
        onOk={() => void handleCreate()}
        onCancel={() => { setCreateOpen(false); setCreateName(""); }}
        confirmLoading={createBusy}
        style={{ background: C.panel }}
        width={280}
        okText="Create"
      >
        <Input
          size="small"
          placeholder="Watchlist name"
          value={createName}
          onChange={(e) => setCreateName(e.target.value)}
          onPressEnter={() => void handleCreate()}
          autoFocus
        />
      </Modal>

      {/* Rename Modal */}
      <Modal
        open={renameId !== null}
        title={<span style={{ fontSize: 12, color: C.text }}>Rename Watchlist</span>}
        onOk={() => void handleRename()}
        onCancel={() => setRenameId(null)}
        confirmLoading={renameBusy}
        style={{ background: C.panel }}
        width={280}
        okText="Save"
      >
        <Input
          size="small"
          value={renameName}
          onChange={(e) => setRenameName(e.target.value)}
          onPressEnter={() => void handleRename()}
          autoFocus
        />
      </Modal>
    </div>
  );
}

interface ItemProps {
  watchlist: Watchlist;
  isActive: boolean;
  onSelect: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

function WatchlistItem({ watchlist, isActive, onSelect, onEdit, onDelete }: ItemProps) {
  const [hovered, setHovered] = useState(false);

  return (
    <div
      onClick={onSelect}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        padding: "5px 10px",
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        background: isActive ? C.active : hovered ? "#1a1a1a" : "transparent",
        borderLeft: isActive ? `2px solid ${C.activeBorder}` : "2px solid transparent",
        transition: "background 0.1s",
      }}
    >
      <span
        style={{
          fontSize: 12,
          color: isActive ? "#fff" : C.text,
          fontWeight: isActive ? 600 : 400,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
          flex: 1,
        }}
      >
        {watchlist.name}
      </span>

      {hovered && (
        <div style={{ display: "flex", gap: 2, marginLeft: 4 }} onClick={(e) => e.stopPropagation()}>
          <Tooltip title="Rename" placement="right">
            <Button
              size="small"
              type="text"
              icon={<EditOutlined style={{ fontSize: 10, color: C.label }} />}
              onClick={onEdit}
              style={{ padding: "0 3px", height: 18 }}
            />
          </Tooltip>
          <Tooltip title="Delete" placement="right">
            <Button
              size="small"
              type="text"
              icon={<DeleteOutlined style={{ fontSize: 10, color: C.danger }} />}
              onClick={onDelete}
              style={{ padding: "0 3px", height: 18 }}
            />
          </Tooltip>
        </div>
      )}
    </div>
  );
}
