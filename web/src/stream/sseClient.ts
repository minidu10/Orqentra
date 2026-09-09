/**
 * A fetch-based Server-Sent Events reader.
 *
 * The browser's native EventSource cannot set request headers, so it cannot send
 * Authorization: Bearer. The notification service requires exactly that and deliberately
 * refuses a token passed as a query parameter, because a long-lived JWT in a URL leaks
 * into browser history, proxy logs and Referer headers. Reading the stream with fetch
 * keeps the token in a header where it belongs.
 *
 * The cost is that the wire format has to be parsed here: SSE frames are separated by a
 * blank line, a frame may carry several data: lines that join with newlines, lines
 * starting with ':' are comments used as heartbeats, and a network read can split a
 * frame anywhere, including mid-line.
 */

export interface SseMessage {
  event: string;
  data: string;
}

export interface SseHandlers {
  onOpen?: () => void;
  onMessage?: (message: SseMessage) => void;
  onError?: (error: unknown) => void;
}

export async function readSseStream(
  url: string,
  token: string,
  signal: AbortSignal,
  handlers: SseHandlers,
): Promise<void> {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "text/event-stream",
    },
    signal,
  });

  if (!response.ok || !response.body) {
    throw new Error(`Stream failed to open (${response.status})`);
  }

  handlers.onOpen?.();

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) return;

      // stream: true matters, because a multi-byte character can straddle two chunks.
      buffer += decoder.decode(value, { stream: true });

      // A frame ends at a blank line. Anything after the last separator is a partial
      // frame and stays in the buffer until the rest of it arrives.
      let separator = buffer.indexOf("\n\n");
      while (separator !== -1) {
        const frame = buffer.slice(0, separator);
        buffer = buffer.slice(separator + 2);

        const message = parseFrame(frame);
        if (message) {
          handlers.onMessage?.(message);
        }
        separator = buffer.indexOf("\n\n");
      }
    }
  } finally {
    reader.releaseLock();
  }
}

function parseFrame(frame: string): SseMessage | null {
  let event = "message";
  const dataLines: string[] = [];

  for (const rawLine of frame.split("\n")) {
    const line = rawLine.endsWith("\r") ? rawLine.slice(0, -1) : rawLine;

    // Comment lines are the heartbeat. They keep the connection alive and carry nothing.
    if (line.startsWith(":") || line.length === 0) {
      continue;
    }

    const colon = line.indexOf(":");
    const field = colon === -1 ? line : line.slice(0, colon);
    // One optional space after the colon is part of the framing, not the value.
    let value = colon === -1 ? "" : line.slice(colon + 1);
    if (value.startsWith(" ")) {
      value = value.slice(1);
    }

    if (field === "event") {
      event = value;
    } else if (field === "data") {
      dataLines.push(value);
    }
  }

  if (dataLines.length === 0) {
    return null;
  }

  return { event, data: dataLines.join("\n") };
}
