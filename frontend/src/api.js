const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

async function request(path, token, options = {}) {
  const headers = new Headers(options.headers);
  headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    const body = await response.text();
    let detail = `Request failed (${response.status}).`;

    if (body) {
      try {
        const problem = JSON.parse(body);
        detail = problem.detail || problem.message || problem.title || detail;
        if (problem.code) detail = `${problem.code}: ${detail}`;
      } catch {
        detail = body;
      }
    }

    if (response.status === 401) {
      throw new Error('Authorization failed. Check that the bearer token is valid and has not expired.');
    }

    throw new Error(detail);
  }

  if (response.status === 204) return null;

  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('application/json') ? response.json() : null;
}

export function listDocuments(token) {
  return request('/api/v1/documents', token);
}

export function uploadDocument(token, file) {
  const body = new FormData();
  body.append('file', file);
  return request('/api/v1/documents/upload', token, { method: 'POST', body });
}

export function deleteDocument(token, documentId) {
  return request(`/api/v1/documents/${encodeURIComponent(documentId)}`, token, { method: 'DELETE' });
}

function parseEvent(block) {
  let event = 'message';
  const data = [];

  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
  }

  if (data.length === 0) return null;

  const rawData = data.join('\n');
  try {
    return { event, data: JSON.parse(rawData) };
  } catch (error) {
    throw new Error(`The chat stream returned invalid ${event} data: ${error.message}`);
  }
}

async function* readEvents(response) {
  if (!response.body) {
    throw new Error('The browser could not read the chat response stream.');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { value, done } = await reader.read();
      buffer += decoder.decode(value, { stream: !done });
      const blocks = buffer.split(/\r?\n\r?\n/);
      buffer = blocks.pop() || '';

      for (const block of blocks) {
        const event = parseEvent(block);
        if (event) yield event;
      }

      if (done) break;
    }

    if (buffer.trim()) {
      const event = parseEvent(buffer);
      if (event) yield event;
    }
  } finally {
    reader.releaseLock();
  }
}

export async function streamChat(token, payload, onToken) {
  const response = await fetch(`${API_BASE_URL}/api/v1/chat/stream`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const body = await response.text();
    let detail = `Chat request failed (${response.status}).`;

    if (body) {
      try {
        const problem = JSON.parse(body);
        detail = problem.detail || problem.message || problem.title || detail;
        if (problem.code) detail = `${problem.code}: ${detail}`;
      } catch {
        detail = body;
      }
    }

    if (response.status === 401) {
      throw new Error('Authorization failed. Check that the bearer token is valid and has not expired.');
    }

    throw new Error(detail);
  }

  let sources = [];
  let receivedMetadata = false;
  for await (const event of readEvents(response)) {
    if (event.event === 'error') {
      throw new Error(event.data?.detail || event.data?.message || 'The chat stream reported an error.');
    }

    if (event.event === 'token') {
      if (typeof event.data?.token !== 'string') {
        throw new Error('The chat stream returned a token event without text.');
      }
      onToken(event.data.token);
    } else if (event.event === 'metadata') {
      if (!Array.isArray(event.data?.sources)) {
        throw new Error('The chat stream returned invalid citation metadata.');
      }
      sources = event.data.sources;
      receivedMetadata = true;
    }
  }

  if (!receivedMetadata) {
    throw new Error('The chat stream ended before citation metadata was received.');
  }

  return sources;
}
