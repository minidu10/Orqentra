import { useCallback, useEffect, useState } from "react";
import { api } from "../api/endpoints";
import type { DlqMessage, DlqTopic, StuckOrder } from "../types";

export function AdminPage() {
  const [topics, setTopics] = useState<DlqTopic[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [messages, setMessages] = useState<DlqMessage[]>([]);
  const [stuck, setStuck] = useState<StuckOrder[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [t, s] = await Promise.all([api.dlqTopics(), api.stuckOrders()]);
      setTopics(t);
      setStuck(s);
      setError(null);
    } catch (err) {
      // A RESTAURANT token gets 403 here, from the gateway, even if the URL was typed
      // in directly. The message is shown rather than the page pretending to work.
      setError(err instanceof Error ? err.message : "Could not load admin data");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openTopic = async (topic: string) => {
    setSelected(topic);
    try {
      setMessages(await api.dlqMessages(topic));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load messages");
    }
  };

  const replay = async (topic: string) => {
    try {
      const result = await api.replayDlq(topic);
      setNotice(`Replayed ${result.replayed} message(s) from ${topic} to ${result.replayedTo}.`);
      await load();
      if (selected === topic) await openTopic(topic);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Replay failed");
    }
  };

  if (error) {
    return (
      <div>
        <h1 className="text-base font-semibold">Admin</h1>
        <p role="alert" data-testid="admin-error" className="mt-4 text-sm text-rose-700">
          {error}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <section>
        <h1 className="text-base font-semibold">Dead letter topics</h1>
        {notice && <p className="mt-2 text-xs text-emerald-700">{notice}</p>}

        {topics.length === 0 ? (
          <p className="mt-3 text-sm text-stone-500">No dead letter topics.</p>
        ) : (
          <table className="mt-3 w-full text-sm">
            <thead>
              <tr className="border-b border-stone-200 text-left text-xs text-stone-500">
                <th className="py-2">Topic</th>
                <th className="text-right">Messages</th>
                <th className="w-44" />
              </tr>
            </thead>
            <tbody>
              {topics.map((topic) => (
                <tr key={topic.topic} className="border-b border-stone-100">
                  <td className="py-2 font-mono text-xs">{topic.topic}</td>
                  <td className="text-right tabular-nums">{topic.messageCount}</td>
                  <td className="py-1.5 text-right">
                    <button
                      onClick={() => void openTopic(topic.topic)}
                      className="mr-2 rounded border border-stone-300 px-2 py-1 text-xs"
                    >
                      Inspect
                    </button>
                    <button
                      onClick={() => void replay(topic.topic)}
                      className="rounded bg-stone-900 px-2 py-1 text-xs text-white"
                    >
                      Replay
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {selected && (
        <section>
          <h2 className="text-sm font-semibold">
            Messages in <span className="font-mono">{selected}</span>
          </h2>
          {messages.length === 0 ? (
            <p className="mt-2 text-sm text-stone-500">Empty.</p>
          ) : (
            <ul className="mt-3 space-y-3">
              {messages.map((message) => (
                <li
                  key={`${message.partition}-${message.offset}`}
                  className="rounded border border-stone-200 bg-white p-3"
                >
                  <div className="text-xs text-stone-500">
                    offset {message.offset} · key {message.key ?? "—"} · from{" "}
                    {message.originalTopic ?? "—"}
                  </div>
                  <div className="mt-1 text-xs text-rose-700">{message.failureReason}</div>
                  <pre className="mt-2 overflow-x-auto rounded bg-stone-50 p-2 text-xs">
                    {message.payload}
                  </pre>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      <section>
        <h2 className="text-sm font-semibold">Stuck orders</h2>
        {stuck.length === 0 ? (
          <p className="mt-2 text-sm text-stone-500">None.</p>
        ) : (
          <table className="mt-3 w-full text-sm">
            <thead>
              <tr className="border-b border-stone-200 text-left text-xs text-stone-500">
                <th className="py-2">Reference</th>
                <th>Status</th>
                <th className="text-right">Age</th>
              </tr>
            </thead>
            <tbody>
              {stuck.map((order) => (
                <tr key={order.reference} className="border-b border-stone-100">
                  <td className="py-2 font-mono text-xs">{order.reference}</td>
                  <td>{order.status}</td>
                  <td className="text-right tabular-nums">
                    {Math.floor(order.ageSeconds / 60)}m
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
