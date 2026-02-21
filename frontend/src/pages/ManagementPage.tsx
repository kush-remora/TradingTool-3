import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import {
  Button,
  Input,
  Popconfirm,
  Rate,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from "antd";
import { useMemo, useState } from "react";
import { useWatchlistData } from "../hooks/useWatchlistData";
import type { Stock, Tag as TagType, Watchlist } from "../types";
import { deleteJson, postJson } from "../utils/api";

export function ManagementPage() {
  const data = useWatchlistData();
  const [messageApi, contextHolder] = message.useMessage();

  const stockTagMap = useMemo(() => {
    const tagById = new Map(data.allTags.map((t) => [t.id, t]));
    const map = new Map<number, TagType[]>();
    data.stockTags.forEach((st) => {
      const tag = tagById.get(st.tagId);
      if (!tag) return;
      map.set(st.stockId, [...(map.get(st.stockId) ?? []), tag]);
    });
    return map;
  }, [data.stockTags, data.allTags]);

  const watchlistTagMap = useMemo(() => {
    const tagById = new Map(data.allTags.map((t) => [t.id, t]));
    const map = new Map<number, TagType[]>();
    data.watchlistTags.forEach((wt) => {
      const tag = tagById.get(wt.tagId);
      if (!tag) return;
      map.set(wt.watchlistId, [...(map.get(wt.watchlistId) ?? []), tag]);
    });
    return map;
  }, [data.watchlistTags, data.allTags]);

  const tabs = [
    { key: "stocks", label: "Stocks", children: <StocksTab data={data} stockTagMap={stockTagMap} messageApi={messageApi} /> },
    { key: "watchlists", label: "Watchlists", children: <WatchlistsTab data={data} watchlistTagMap={watchlistTagMap} messageApi={messageApi} /> },
    { key: "tags", label: "Tags", children: <TagsTab data={data} messageApi={messageApi} /> },
  ];

  return (
    <div style={{ padding: "16px 24px" }}>
      {contextHolder}
      <Typography.Title level={5} style={{ marginBottom: 16 }}>
        Management
      </Typography.Title>
      <Tabs items={tabs} defaultActiveKey="stocks" />
    </div>
  );
}

// ==================== Stocks Tab ====================

function StocksTab({
  data,
  stockTagMap,
  messageApi,
}: {
  data: ReturnType<typeof useWatchlistData>;
  stockTagMap: Map<number, TagType[]>;
  messageApi: ReturnType<typeof message.useMessage>[0];
}) {
  const [tagInputs, setTagInputs] = useState<Record<number, string>>({});

  const columns = [
    {
      title: "Symbol",
      dataIndex: "symbol",
      key: "symbol",
      width: 100,
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    {
      title: "Company",
      dataIndex: "companyName",
      key: "companyName",
      ellipsis: true,
    },
    {
      title: "Priority",
      dataIndex: "priority",
      key: "priority",
      width: 130,
      render: (_: unknown, stock: Stock) => (
        <Rate
          count={5}
          value={stock.priority ?? 0}
          onChange={(val) => {
            void data.updateStock(stock.id, { priority: val }).catch((e: Error) =>
              messageApi.error(e.message),
            );
          }}
          style={{ fontSize: 12 }}
        />
      ),
    },
    {
      title: "Tags",
      key: "tags",
      render: (_: unknown, stock: Stock) => {
        const tags = stockTagMap.get(stock.id) ?? [];
        return (
          <Space wrap size={4}>
            {tags.map((tag) => (
              <Tag
                key={tag.id}
                closable
                color="cyan"
                style={{ margin: 0 }}
                onClose={(e) => {
                  e.preventDefault();
                  void data.removeTagFromStock(stock.id, tag.id).catch((err: Error) =>
                    messageApi.error(err.message),
                  );
                }}
              >
                {tag.name}
              </Tag>
            ))}
            <Input
              size="small"
              placeholder="+ tag"
              style={{ width: 80, fontSize: 11 }}
              value={tagInputs[stock.id] ?? ""}
              onChange={(e) => setTagInputs((prev) => ({ ...prev, [stock.id]: e.target.value }))}
              onPressEnter={() => {
                const name = (tagInputs[stock.id] ?? "").trim();
                if (!name) return;
                void data.addTagToStock(stock.id, name)
                  .then(() => setTagInputs((prev) => ({ ...prev, [stock.id]: "" })))
                  .catch((e: Error) => messageApi.error(e.message));
              }}
            />
          </Space>
        );
      },
    },
    {
      title: "Description",
      key: "description",
      render: (_: unknown, stock: Stock) => (
        <Input.TextArea
          defaultValue={stock.description ?? ""}
          rows={1}
          autoSize={{ minRows: 1, maxRows: 3 }}
          style={{ fontSize: 12 }}
          onBlur={(e) => {
            const desc = e.target.value.trim();
            if (desc === (stock.description ?? "")) return;
            void data.updateStock(stock.id, { description: desc }).catch((err: Error) =>
              messageApi.error(err.message),
            );
          }}
        />
      ),
    },
  ];

  return (
    <Table
      rowKey="id"
      size="small"
      dataSource={data.stocks}
      columns={columns}
      loading={data.loading}
      pagination={{ pageSize: 20 }}
    />
  );
}

// ==================== Watchlists Tab ====================

function WatchlistsTab({
  data,
  watchlistTagMap,
  messageApi,
}: {
  data: ReturnType<typeof useWatchlistData>;
  watchlistTagMap: Map<number, TagType[]>;
  messageApi: ReturnType<typeof message.useMessage>[0];
}) {
  const [tagInputs, setTagInputs] = useState<Record<number, string>>({});

  const columns = [
    {
      title: "Name",
      dataIndex: "name",
      key: "name",
      width: 180,
      render: (v: string) => <Typography.Text strong>{v}</Typography.Text>,
    },
    {
      title: "Tags",
      key: "tags",
      render: (_: unknown, wl: Watchlist) => {
        const tags = watchlistTagMap.get(wl.id) ?? [];
        return (
          <Space wrap size={4}>
            {tags.map((tag) => (
              <Tag
                key={tag.id}
                closable
                color="geekblue"
                style={{ margin: 0 }}
                onClose={(e) => {
                  e.preventDefault();
                  void data.removeTagFromWatchlist(wl.id, tag.id).catch((err: Error) =>
                    messageApi.error(err.message),
                  );
                }}
              >
                {tag.name}
              </Tag>
            ))}
            <Input
              size="small"
              placeholder="+ tag"
              style={{ width: 80, fontSize: 11 }}
              value={tagInputs[wl.id] ?? ""}
              onChange={(e) => setTagInputs((prev) => ({ ...prev, [wl.id]: e.target.value }))}
              onPressEnter={() => {
                const name = (tagInputs[wl.id] ?? "").trim();
                if (!name) return;
                void data.addTagToWatchlist(wl.id, name)
                  .then(() => setTagInputs((prev) => ({ ...prev, [wl.id]: "" })))
                  .catch((e: Error) => messageApi.error(e.message));
              }}
            />
          </Space>
        );
      },
    },
    {
      title: "Description",
      key: "description",
      render: (_: unknown, wl: Watchlist) => (
        <Input.TextArea
          defaultValue={wl.description ?? ""}
          rows={1}
          autoSize={{ minRows: 1, maxRows: 3 }}
          style={{ fontSize: 12 }}
          onBlur={(e) => {
            const desc = e.target.value.trim();
            if (desc === (wl.description ?? "")) return;
            void data.updateWatchlist(wl.id, { description: desc }).catch((err: Error) =>
              messageApi.error(err.message),
            );
          }}
        />
      ),
    },
    {
      title: "",
      key: "actions",
      width: 60,
      render: (_: unknown, wl: Watchlist) => (
        <Popconfirm
          title="Delete this watchlist?"
          okText="Delete"
          okButtonProps={{ danger: true }}
          onConfirm={() => void data.deleteWatchlist(wl.id).catch((e: Error) => messageApi.error(e.message))}
        >
          <Button danger size="small" icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ];

  return (
    <Table
      rowKey="id"
      size="small"
      dataSource={data.watchlists}
      columns={columns}
      loading={data.loading}
      pagination={false}
    />
  );
}

// ==================== Tags Tab ====================

function TagsTab({
  data,
  messageApi,
}: {
  data: ReturnType<typeof useWatchlistData>;
  messageApi: ReturnType<typeof message.useMessage>[0];
}) {
  const [newTagName, setNewTagName] = useState("");
  const [creating, setCreating] = useState(false);

  const handleCreate = async () => {
    const name = newTagName.trim();
    if (!name) return;
    setCreating(true);
    try {
      await postJson("/api/watchlist/tags", { name });
      setNewTagName("");
      await data.refetch();
    } catch (e) {
      messageApi.error(e instanceof Error ? e.message : "Failed to create tag");
    } finally {
      setCreating(false);
    }
  };

  return (
    <div style={{ maxWidth: 480 }}>
      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder="New tag name"
          value={newTagName}
          onChange={(e) => setNewTagName(e.target.value)}
          onPressEnter={() => void handleCreate()}
          style={{ width: 240 }}
        />
        <Button
          type="primary"
          icon={<PlusOutlined />}
          loading={creating}
          onClick={() => void handleCreate()}
        >
          Create Tag
        </Button>
      </Space>

      <Space wrap size={8}>
        {data.allTags.map((tag) => (
          <Tag
            key={tag.id}
            color="purple"
            closable
            style={{ fontSize: 13, padding: "2px 10px" }}
            onClose={(e) => {
              e.preventDefault();
              void deleteJson(`/api/watchlist/tags/${tag.id}`)
                .then(() => data.refetch())
                .catch((err: Error) => messageApi.error(err.message));
            }}
          >
            {tag.name}
          </Tag>
        ))}
        {data.allTags.length === 0 && !data.loading && (
          <Typography.Text type="secondary">No tags yet.</Typography.Text>
        )}
      </Space>
    </div>
  );
}
