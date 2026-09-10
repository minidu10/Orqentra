import { describe, expect, it, vi } from "vitest";
import { readSseStream, type SseMessage } from "./sseClient";

/** Builds a fetch Response whose body streams the given chunks, one write per chunk. */
function fakeSseResponse(chunks: string[]): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });
  return new Response(body, { status: 200 });
}

async function collect(chunks: string[]): Promise<SseMessage[]> {
  const messages: SseMessage[] = [];
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(fakeSseResponse(chunks)));

  await readSseStream("http://example.test/stream", "token", new AbortController().signal, {
    onMessage: (message) => messages.push(message),
  });

  return messages;
}

describe("readSseStream", () => {
  it("joins multi-line data: fields with a newline", async () => {
    const messages = await collect([
      'event: order.status\ndata: {"a":1,\ndata: "b":2}\n\n',
    ]);

    expect(messages).toEqual([
      { event: "order.status", data: '{"a":1,\n"b":2}' },
    ]);
  });

  it("ignores comment lines used as heartbeats", async () => {
    const messages = await collect([
      ":heartbeat\n\n",
      'event: order.status\ndata: {"ok":true}\n\n',
      ":heartbeat\n\n",
    ]);

    expect(messages).toHaveLength(1);
    expect(messages[0]).toEqual({ event: "order.status", data: '{"ok":true}' });
  });

  it("reassembles a frame split across two chunks", async () => {
    // The colon lands in the middle of "event:order.status", split mid-line, and the
    // terminating blank line arrives only in the second chunk.
    const messages = await collect([
      "event:order.st",
      'atus\ndata:{"reference":"abc"}\n\n',
    ]);

    expect(messages).toEqual([
      { event: "order.status", data: '{"reference":"abc"}' },
    ]);
  });

  it("reassembles a frame split mid multi-byte character", async () => {
    // "café" — the é is encoded as two UTF-8 bytes; split the chunk so the second byte
    // lands in the next read. Only correct if the decoder is told stream: true.
    const full = new TextEncoder().encode('data:café\n\n');
    const first = full.slice(0, full.length - 2);
    const second = full.slice(full.length - 2);

    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(first);
        controller.enqueue(second);
        controller.close();
      },
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(body, { status: 200 })));

    const messages: SseMessage[] = [];
    await readSseStream("http://example.test/stream", "token", new AbortController().signal, {
      onMessage: (message) => messages.push(message),
    });

    expect(messages).toEqual([{ event: "message", data: "café" }]);
  });

  it("calls onOpen once the response arrives, before any message", async () => {
    const events: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(fakeSseResponse(['data: hello\n\n'])),
    );

    await readSseStream("http://example.test/stream", "token", new AbortController().signal, {
      onOpen: () => events.push("open"),
      onMessage: () => events.push("message"),
    });

    expect(events).toEqual(["open", "message"]);
  });

  it("sends the token as an Authorization header, never as a query parameter", async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeSseResponse([]));
    vi.stubGlobal("fetch", fetchMock);

    await readSseStream("http://example.test/stream", "secret-token", new AbortController().signal, {});

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://example.test/stream");
    expect(url).not.toContain("secret-token");
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer secret-token");
  });
});
