import { useEffect, useRef, useState } from "react";
import { Button, Input } from "antd";
import { SendOutlined } from "@ant-design/icons";
import { sendRequest } from "../utils/api";

const C = {
  bg: "#141414",
  border: "#1f1f1f",
  text: "#ccc",
  label: "#888",
  accent: "#26a69a",
  you: "#1d4ed8",
  error: "#ef5350",
};

interface Message {
  id: string;
  role: "you" | "bot" | "error";
  text: string;
  time: string;
}

// Telegram SVG icon (monochrome)
function TelegramIcon({ size = 20 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor">
      <path d="M11.944 0A12 12 0 0 0 0 12a12 12 0 0 0 12 12 12 12 0 0 0 12-12A12 12 0 0 0 12 0a12 12 0 0 0-.056 0zm4.962 7.224c.1-.002.321.023.465.14a.506.506 0 0 1 .171.325c.016.093.036.306.02.472-.18 1.898-.962 6.502-1.36 8.627-.168.9-.499 1.201-.82 1.23-.696.065-1.225-.46-1.9-.902-1.056-.693-1.653-1.124-2.678-1.8-1.185-.78-.417-1.21.258-1.91.177-.184 3.247-2.977 3.307-3.23.007-.032.014-.15-.056-.212s-.174-.041-.249-.024c-.106.024-1.793 1.14-5.061 3.345-.48.33-.913.49-1.302.48-.428-.008-1.252-.241-1.865-.44-.752-.245-1.349-.374-1.297-.789.027-.216.325-.437.893-.663 3.498-1.524 5.83-2.529 6.998-3.014 3.332-1.386 4.025-1.627 4.476-1.635z" />
    </svg>
  );
}

export function TelegramChatWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) {
      bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages, open]);

  const append = (role: Message["role"], text: string) => {
    setMessages((prev) => [
      ...prev,
      { id: `${Date.now()}-${Math.random()}`, role, text, time: new Date().toLocaleTimeString() },
    ]);
  };

  const handleSend = async () => {
    const text = input.trim();
    if (!text || sending) return;
    setSending(true);
    setInput("");
    append("you", text);

    try {
      const payload = await sendRequest("/api/telegram/send/text", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ text }),
      });
      append("bot", (payload.message as string | undefined) ?? "Sent ✓");
    } catch (e) {
      append("error", e instanceof Error ? e.message : "Send failed");
    } finally {
      setSending(false);
    }
  };

  return (
    <div style={{ position: "fixed", bottom: 24, right: 24, zIndex: 1000 }}>
      {/* Chat window */}
      {open && (
        <div
          style={{
            width: 340,
            height: 400,
            background: C.bg,
            border: `1px solid ${C.border}`,
            borderRadius: 8,
            display: "flex",
            flexDirection: "column",
            marginBottom: 10,
            boxShadow: "0 8px 32px rgba(0,0,0,0.6)",
          }}
        >
          {/* Header */}
          <div
            style={{
              padding: "7px 12px",
              borderBottom: `1px solid ${C.border}`,
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
              <span style={{ color: C.accent }}>
                <TelegramIcon size={13} />
              </span>
              <span style={{ fontSize: 11, color: C.text, fontWeight: 600 }}>Trading Alerts</span>
            </div>
            <Button
              size="small"
              type="text"
              onClick={() => setOpen(false)}
              style={{ color: C.label, fontSize: 11, padding: "0 4px", height: 18 }}
            >
              ✕
            </Button>
          </div>

          {/* Messages */}
          <div style={{ flex: 1, overflowY: "auto", padding: "8px 10px", display: "flex", flexDirection: "column", gap: 6 }}>
            {messages.length === 0 && (
              <span style={{ fontSize: 11, color: C.label }}>Send a message to Telegram…</span>
            )}
            {messages.map((m) => (
              <div
                key={m.id}
                style={{
                  alignSelf: m.role === "you" ? "flex-end" : "flex-start",
                  maxWidth: "80%",
                }}
              >
                <div
                  style={{
                    background:
                      m.role === "you" ? C.you : m.role === "error" ? "#2d1111" : "#1a2a1a",
                    borderRadius: 6,
                    padding: "4px 8px",
                    fontSize: 11,
                    color: m.role === "error" ? C.error : C.text,
                  }}
                >
                  {m.text}
                </div>
                <span style={{ fontSize: 9, color: C.label, paddingLeft: 2 }}>{m.time}</span>
              </div>
            ))}
            <div ref={bottomRef} />
          </div>

          {/* Footer input */}
          <div
            style={{
              padding: "6px 8px",
              borderTop: `1px solid ${C.border}`,
              display: "flex",
              gap: 6,
            }}
          >
            <Input
              size="small"
              value={input}
              placeholder="Message…"
              onChange={(e) => setInput(e.target.value)}
              onPressEnter={() => void handleSend()}
              style={{ flex: 1, fontSize: 11 }}
            />
            <Button
              size="small"
              type="primary"
              icon={<SendOutlined style={{ fontSize: 11 }} />}
              loading={sending}
              onClick={() => void handleSend()}
              style={{ background: C.accent, borderColor: C.accent }}
            />
          </div>
        </div>
      )}

      {/* FAB */}
      <Button
        type="primary"
        shape="circle"
        size="large"
        onClick={() => setOpen((v) => !v)}
        style={{
          background: C.accent,
          borderColor: C.accent,
          width: 48,
          height: 48,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          boxShadow: "0 4px 16px rgba(38,166,154,0.4)",
          float: "right",
        }}
        icon={<TelegramIcon size={22} />}
      />
    </div>
  );
}
