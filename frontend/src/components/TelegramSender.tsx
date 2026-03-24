import { useState } from "react";
import { Alert, Button, Card, Input, Space, Upload } from "antd";
import type { UploadFile } from "antd";
import { sendRequest } from "../utils/api";

type SendStatus = { type: "success" | "error"; message: string } | null;

// Routes to the correct endpoint based on file extension / MIME type.
function detectEndpoint(file: File): string | null {
  const name = file.name.toLowerCase();
  const mime = file.type.toLowerCase();
  if (mime.startsWith("image/") || [".png", ".jpg", ".jpeg", ".webp"].some((ext) => name.endsWith(ext))) {
    return "/api/telegram/send/image";
  }
  if (name.endsWith(".xls") || name.endsWith(".xlsx")) {
    return "/api/telegram/send/excel";
  }
  return null;
}

export function TelegramSender() {
  const [text, setText] = useState("");
  const [caption, setCaption] = useState("");
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [sending, setSending] = useState(false);
  const [status, setStatus] = useState<SendStatus>(null);

  const showStatus = (type: "success" | "error", message: string) => {
    setStatus({ type, message });
    setTimeout(() => setStatus(null), 3000);
  };

  const handleSendText = async () => {
    const trimmed = text.trim();
    if (!trimmed || sending) return;
    setSending(true);
    try {
      const res = await sendRequest("/api/telegram/send/text", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: trimmed }),
      });
      setText("");
      showStatus("success", (res.message as string | undefined) ?? "Text sent.");
    } catch (e) {
      showStatus("error", e instanceof Error ? e.message : "Failed to send text.");
    } finally {
      setSending(false);
    }
  };

  const handleSendFile = async () => {
    const file = fileList[0]?.originFileObj ?? null;
    if (!file || sending) return;

    const endpoint = detectEndpoint(file);
    if (!endpoint) {
      showStatus("error", "Unsupported file type. Use PNG/JPG/WEBP or XLS/XLSX.");
      return;
    }

    setSending(true);
    const formData = new FormData();
    formData.append("file", file, file.name);
    if (caption.trim()) formData.append("caption", caption.trim());

    try {
      const res = await sendRequest(endpoint, { method: "POST", body: formData });
      setFileList([]);
      setCaption("");
      showStatus("success", (res.message as string | undefined) ?? "File sent.");
    } catch (e) {
      showStatus("error", e instanceof Error ? e.message : "Failed to send file.");
    } finally {
      setSending(false);
    }
  };

  return (
    <Card size="small" title="Telegram">
      <Space direction="vertical" size="small" style={{ width: "100%" }}>
        {status && (
          <Alert
            type={status.type}
            message={status.message}
            showIcon
            closable
            onClose={() => setStatus(null)}
          />
        )}

        {/* Text send */}
        <Space.Compact style={{ width: "100%" }}>
          <Input
            value={text}
            placeholder="Message..."
            onChange={(e) => setText(e.target.value)}
            onPressEnter={() => void handleSendText()}
          />
          <Button type="primary" loading={sending} onClick={() => void handleSendText()}>
            Send
          </Button>
        </Space.Compact>

        {/* File send — caption + send button only appear once a file is attached */}
        <Space direction="vertical" size={4} style={{ width: "100%" }}>
          <Upload
            maxCount={1}
            accept="image/*,.xls,.xlsx"
            beforeUpload={() => false}
            fileList={fileList}
            onChange={({ fileList: updated }) => setFileList(updated.slice(-1))}
          >
            <Button size="small">Attach Image or Excel</Button>
          </Upload>
          {fileList.length > 0 && (
            <Space.Compact style={{ width: "100%" }}>
              <Input
                size="small"
                value={caption}
                placeholder="Optional caption"
                onChange={(e) => setCaption(e.target.value)}
              />
              <Button
                size="small"
                type="primary"
                loading={sending}
                onClick={() => void handleSendFile()}
              >
                Send File
              </Button>
            </Space.Compact>
          )}
        </Space>
      </Space>
    </Card>
  );
}
