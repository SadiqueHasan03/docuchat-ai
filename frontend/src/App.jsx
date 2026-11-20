import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { deleteDocument, listDocuments, streamChat, uploadDocument } from './api.js';

const ThreeScene = lazy(() => import('./ThreeScene.jsx'));

const iconPaths = {
  chat: 'M20 11.5a7.5 7.5 0 0 1-7.5 7.5 8.3 8.3 0 0 1-3.7-.9L4 20l1.2-4.1a7.5 7.5 0 1 1 14.8-4.4Z',
  files: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Zm0 0v6h6M8 13h8M8 17h8',
  plus: 'M12 5v14M5 12h14',
  upload: 'M12 16V4m0 0L7 9m5-5 5 5M4 16v4h16v-4',
  send: 'm22 2-7 20-4-9-9-4 20-7ZM22 2 11 13',
  settings: 'M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm7.4-3.5a7.5 7.5 0 0 0-.1-1.2l2-1.5-2-3.5-2.4 1a7.6 7.6 0 0 0-2-1.2L14.5 3h-5l-.4 2.6a7.6 7.6 0 0 0-2 1.2l-2.4-1-2 3.5 2 1.5a7.5 7.5 0 0 0 0 2.4l-2 1.5 2 3.5 2.4-1a7.6 7.6 0 0 0 2 1.2l.4 2.6h5l.4-2.6a7.6 7.6 0 0 0 2-1.2l2.4 1 2-3.5-2-1.5a7.5 7.5 0 0 0 .1-1.2Z',
  trash: 'M3 6h18M8 6V4h8v2m3 0-.8 14H5.8L5 6m5 4v6m4-6v6',
  close: 'M18 6 6 18M6 6l12 12',
  lock: 'M5 10h14v11H5zM8 10V7a4 4 0 0 1 8 0v3',
  spark: 'm12 3 1.9 5.8L20 11l-6.1 2.2L12 19l-1.9-5.8L4 11l6.1-2.2L12 3Zm7 12 .9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9L19 15Z',
  refresh: 'M20 7v5h-5M4 17v-5h5m-4.1-2A7.5 7.5 0 0 1 18 7l2 5M4 12l2 5a7.5 7.5 0 0 0 13.1-3',
  check: 'm5 12 4 4L19 6',
};

function Icon({ name, size = 18, className = '' }) {
  return (
    <svg
      aria-hidden="true"
      className={className}
      fill="none"
      height={size}
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.7"
      viewBox="0 0 24 24"
      width={size}
    >
      <path d={iconPaths[name]} />
    </svg>
  );
}

function formatBytes(bytes = 0) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(value) {
  if (!value) return 'Recently added';
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(new Date(value));
}

function getDocumentKind(document) {
  const extension = document.fileName?.split('.').pop()?.toLowerCase();
  if (extension === 'pdf') return 'PDF';
  if (['doc', 'docx'].includes(extension)) return 'DOC';
  if (['md', 'txt'].includes(extension)) return 'TXT';
  return 'FILE';
}

function ErrorBanner({ message, onDismiss }) {
  if (!message) return null;
  return (
    <div className="error-banner" role="alert">
      <span>{message}</span>
      <button aria-label="Dismiss error" className="icon-button" onClick={onDismiss} type="button">
        <Icon name="close" size={16} />
      </button>
    </div>
  );
}

function SourceCards({ sources }) {
  if (!sources?.length) return null;
  return (
    <div className="sources">
      <div className="sources-heading">Sources</div>
      <div className="source-list">
        {sources.map((source, index) => (
          <article className="source-card" key={`${source.documentId}-${source.pageNumber}-${index}`}>
            <div className="source-card-top">
              <span className="source-file"><Icon name="files" size={14} />{source.fileName}</span>
              <span className="source-page">p. {source.pageNumber}</span>
            </div>
            {source.snippet && <p>{source.snippet}</p>}
          </article>
        ))}
      </div>
    </div>
  );
}

function DocumentRow({ document, selected, onSelect, onDelete, compact = false }) {
  const status = document.status?.toLowerCase();
  return (
    <article className={`document-row${compact ? ' document-row-compact' : ''}`}>
      <label className="document-select">
        <input
          aria-label={`Include ${document.fileName} in chat`}
          checked={selected}
          onChange={() => onSelect(document.documentId)}
          type="checkbox"
        />
        <span className={`file-type file-type-${getDocumentKind(document).toLowerCase()}`}>
          {getDocumentKind(document)}
        </span>
      </label>
      <div className="document-info">
        <span className="document-name" title={document.fileName}>{document.fileName}</span>
        <span className="document-meta">
          {formatBytes(document.fileSizeBytes)} <span className="meta-dot">·</span> {document.pageCount || 1} pages
        </span>
        {!compact && (
          <span className={`document-status status-${status || 'unknown'}`}>
            <span className="status-dot" />{status || 'unknown'}
          </span>
        )}
      </div>
      <button
        aria-label={`Delete ${document.fileName}`}
        className="icon-button document-delete"
        onClick={() => onDelete(document)}
        title="Delete document"
        type="button"
      >
        <Icon name="trash" size={16} />
      </button>
    </article>
  );
}

function EmptyDocuments({ onUpload, disabled }) {
  return (
    <div className="empty-documents">
      <div className="empty-icon"><Icon name="files" size={21} /></div>
      <strong>Your library is waiting</strong>
      <p>Add a document to start asking questions. PDF, Word, text, and Markdown files are supported.</p>
      <button className="button button-secondary button-small" disabled={disabled} onClick={onUpload} type="button">
        <Icon name="upload" size={15} /> Add your first file
      </button>
    </div>
  );
}

function SettingsDialog({ draft, setDraft, onClose, onConnect, onDisconnect, connected, busy }) {
  return (
    <div className="dialog-backdrop" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section aria-labelledby="settings-title" aria-modal="true" className="settings-dialog" role="dialog">
        <div className="dialog-heading">
          <div className="dialog-icon"><Icon name="lock" size={19} /></div>
          <button aria-label="Close settings" className="icon-button" onClick={onClose} type="button">
            <Icon name="close" size={18} />
          </button>
        </div>
        <h2 id="settings-title">Connect to your workspace</h2>
        <p className="dialog-description">
          DocuChat uses a bearer token from your identity provider. It stays in this tab’s memory and is never saved to browser storage.
        </p>
        <form onSubmit={onConnect}>
          <label className="field-label" htmlFor="access-token">Bearer token</label>
          <input
            autoComplete="off"
            className="text-input"
            id="access-token"
            onChange={(event) => setDraft(event.target.value)}
            placeholder={connected ? 'Enter a new token to replace the current one' : 'Paste your access token'}
            spellCheck="false"
            type="password"
            value={draft}
          />
          <p className="field-note">The backend validates HS256 JWTs. Do not enter your JWT signing secret here.</p>
          <div className="dialog-actions">
            {connected && (
              <button className="button button-quiet" onClick={onDisconnect} type="button">Disconnect</button>
            )}
            <button className="button button-primary" disabled={busy || !draft.trim()} type="submit">
              {busy ? <span className="spinner" /> : <Icon name="check" size={16} />}
              Connect
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

export default function App() {
  const [token, setToken] = useState('');
  const [tokenDraft, setTokenDraft] = useState('');
  const [showSettings, setShowSettings] = useState(false);
  const [documents, setDocuments] = useState([]);
  const [selectedIds, setSelectedIds] = useState([]);
  const [messages, setMessages] = useState([]);
  const [conversationId, setConversationId] = useState('');
  const [question, setQuestion] = useState('');
  const [activeView, setActiveView] = useState('chat');
  const [error, setError] = useState('');
  const [loadingDocuments, setLoadingDocuments] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const fileInputRef = useRef(null);
  const endOfMessagesRef = useRef(null);
  const questionRef = useRef(null);

  useEffect(() => {
    endOfMessagesRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages]);

  const refreshDocuments = async (accessToken = token) => {
    if (!accessToken) return;
    setLoadingDocuments(true);
    try {
      const result = await listDocuments(accessToken);
      setDocuments(Array.isArray(result) ? result : []);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoadingDocuments(false);
    }
  };

  const handleConnect = async (event) => {
    event.preventDefault();
    const newToken = tokenDraft.trim();
    if (!newToken) return;
    setToken(newToken);
    setTokenDraft('');
    setShowSettings(false);
    await refreshDocuments(newToken);
  };

  const handleDisconnect = () => {
    setToken('');
    setTokenDraft('');
    setDocuments([]);
    setSelectedIds([]);
    setMessages([]);
    setConversationId('');
    setError('');
    setShowSettings(false);
  };

  const handleUpload = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || !token) return;

    setUploading(true);
    setError('');
    try {
      await uploadDocument(token, file);
      await refreshDocuments();
      setActiveView('documents');
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (document) => {
    if (!window.confirm(`Delete "${document.fileName}" and its indexed content?`)) return;
    setError('');
    try {
      await deleteDocument(token, document.documentId);
      setDocuments((current) => current.filter((item) => item.documentId !== document.documentId));
      setSelectedIds((current) => current.filter((id) => id !== document.documentId));
    } catch (requestError) {
      setError(requestError.message);
    }
  };

  const toggleDocument = (documentId) => {
    setSelectedIds((current) => current.includes(documentId)
      ? current.filter((id) => id !== documentId)
      : [...current, documentId]);
  };

  const startNewChat = () => {
    setMessages([]);
    setConversationId('');
    setError('');
    setActiveView('chat');
    questionRef.current?.focus();
  };

  const sendQuestion = async (value) => {
    const text = value.trim();
    if (!text || streaming) return;
    if (!token) {
      setError('Connect your workspace before starting a chat.');
      setShowSettings(true);
      return;
    }

    const nextConversationId = conversationId || globalThis.crypto?.randomUUID?.() || `conv-${Date.now()}`;
    setConversationId(nextConversationId);
    setActiveView('chat');
    setQuestion('');
    setError('');
    const assistantIndex = messages.length + 1;
    setMessages((current) => [
      ...current,
      { role: 'user', content: text },
      { role: 'assistant', content: '', sources: [] },
    ]);
    setStreaming(true);

    try {
      const payload = {
        conversationId: nextConversationId,
        question: text,
      };
      if (selectedIds.length > 0) payload.documentIds = selectedIds;

      const sources = await streamChat(token, payload, (chunk) => {
        setMessages((current) => current.map((message, index) => index === assistantIndex
          ? { ...message, content: message.content + chunk }
          : message));
      });

      setMessages((current) => current.map((message, index) => index === assistantIndex
        ? { ...message, sources }
        : message));
    } catch (requestError) {
      setError(requestError.message);
      setMessages((current) => current.map((message, index) => index === assistantIndex
        ? { ...message, content: message.content || 'I couldn’t complete that response. Please check the connection and try again.' }
        : message));
    } finally {
      setStreaming(false);
    }
  };

  const handleQuestionSubmit = (event) => {
    event.preventDefault();
    sendQuestion(question);
  };

  const handleQuestionKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      sendQuestion(question);
    }
  };

  const selectedCount = selectedIds.length;

  return (
    <div className="app-shell">
      <Suspense fallback={null}><ThreeScene /></Suspense>
      <aside className="sidebar">
        <a aria-label="DocuChat home" className="brand" href="#" onClick={(event) => {
          event.preventDefault();
          setActiveView('chat');
        }}>
          <span className="brand-mark"><span /></span>
          <span>docuchat<span className="brand-period">.</span></span>
        </a>

        <button className="button button-new-chat" onClick={startNewChat} type="button">
          <Icon name="plus" size={17} /> New conversation
          <kbd>⌘ K</kbd>
        </button>

        <div className="sidebar-label">Workspace</div>
        <nav aria-label="Main navigation" className="nav-list">
          <button
            aria-current={activeView === 'chat' ? 'page' : undefined}
            className={`nav-item${activeView === 'chat' ? ' active' : ''}`}
            onClick={() => setActiveView('chat')}
            type="button"
          >
            <Icon name="chat" size={17} /> Chat <span className="nav-active-dot" />
          </button>
          <button
            aria-current={activeView === 'documents' ? 'page' : undefined}
            className={`nav-item${activeView === 'documents' ? ' active' : ''}`}
            onClick={() => setActiveView('documents')}
            type="button"
          >
            <Icon name="files" size={17} /> Documents
            <span className="nav-count">{documents.length}</span>
          </button>
        </nav>

        <div className="sidebar-bottom">
          <div className="privacy-note">
            <div className="privacy-icon"><Icon name="lock" size={14} /></div>
            <div><strong>Your workspace</strong><span>Scoped to your account</span></div>
            <span className={`connection-indicator${token ? ' connected' : ''}`} title={token ? 'Connected' : 'Not connected'} />
          </div>
          <button className="profile-button" onClick={() => {
            setTokenDraft('');
            setShowSettings(true);
          }} type="button">
            <span className="profile-avatar">D</span>
            <span className="profile-copy"><strong>DocuChat user</strong><span>{token ? 'Workspace connected' : 'Connect workspace'}</span></span>
            <Icon name="settings" size={17} />
          </button>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="breadcrumb"><span>Workspace</span><span className="breadcrumb-separator">/</span><strong>{activeView === 'chat' ? 'Chat' : 'Documents'}</strong></div>
          <div className="topbar-actions">
            <div className={`connection-pill${token ? ' is-connected' : ''}`}>
              <span className="connection-indicator" />
              {token ? 'Connected' : 'Not connected'}
            </div>
            <button className="button button-connect" onClick={() => {
              setTokenDraft('');
              setShowSettings(true);
            }} type="button">
              {token ? 'Manage access' : 'Connect workspace'}
            </button>
          </div>
        </header>

        <ErrorBanner message={error} onDismiss={() => setError('')} />

        {activeView === 'chat' ? (
          <div className="workspace-grid">
            <section aria-label="Chat workspace" className="chat-column">
              <div className="chat-heading">
                <div>
                  <div className="eyebrow"><span className="eyebrow-dot" /> YOUR KNOWLEDGE, IN CONTEXT</div>
                  <h1>Ask your documents<span className="heading-period">.</span></h1>
                  <p>Find the signal in your files. Get clear answers, grounded in your sources.</p>
                </div>
                <div className="chat-heading-art">
                  <span className="orbit orbit-one" /><span className="orbit orbit-two" />
                  <span className="orbit-core"><Icon name="spark" size={20} /></span>
                </div>
              </div>

              <div aria-live="polite" className={`conversation${messages.length ? ' has-messages' : ''}`}>
                {messages.length === 0 ? (
                  <div className="welcome-state">
                    <div className="welcome-icon"><Icon name="spark" size={21} /></div>
                    <h2>Start with a question</h2>
                    <p>Your answers are drawn from the documents in your workspace, with page-level citations.</p>
                    <div className="prompt-suggestions">
                      {[
                        'What are the key themes in my documents?',
                        'Summarize the most important findings',
                        'What should I know before getting started?',
                      ].map((prompt) => (
                        <button className="suggestion" key={prompt} onClick={() => sendQuestion(prompt)} type="button">
                          <span>{prompt}</span><Icon name="send" size={14} />
                        </button>
                      ))}
                    </div>
                  </div>
                ) : (
                  <div className="message-list">
                    {messages.map((message, index) => (
                      <article className={`message message-${message.role}`} key={`${index}-${message.role}`}>
                        <div className={`message-avatar message-avatar-${message.role}`}>
                          {message.role === 'assistant' ? <span className="mini-brand-mark" /> : 'Y'}
                        </div>
                        <div className="message-content">
                          <div className="message-author">{message.role === 'assistant' ? 'DocuChat' : 'You'}</div>
                          <p className={message.role === 'assistant' ? 'answer-copy' : 'question-copy'}>
                            {message.content || (streaming && index === messages.length - 1
                              ? <span className="typing-indicator"><i /><i /><i /></span>
                              : '')}
                          </p>
                          {message.role === 'assistant' && message.sources?.length > 0 && <SourceCards sources={message.sources} />}
                        </div>
                      </article>
                    ))}
                    <div ref={endOfMessagesRef} />
                  </div>
                )}
              </div>

              <div className="composer-wrap">
                {!token && (
                  <button className="connect-nudge" onClick={() => setShowSettings(true)} type="button">
                    <Icon name="lock" size={14} /> Connect your workspace to start chatting
                  </button>
                )}
                <form className="composer" onSubmit={handleQuestionSubmit}>
                  <textarea
                    aria-label="Ask a question about your documents"
                    disabled={streaming}
                    onChange={(event) => setQuestion(event.target.value)}
                    onKeyDown={handleQuestionKeyDown}
                    placeholder="Ask anything about your documents..."
                    ref={questionRef}
                    rows={1}
                    value={question}
                  />
                  <div className="composer-footer">
                    <span className="composer-hint">
                      <span className="composer-spark"><Icon name="spark" size={13} /></span>
                      {selectedCount ? `${selectedCount} source${selectedCount === 1 ? '' : 's'} selected` : 'Searching all documents'}
                      <span className="composer-shortcut">↵ to send</span>
                    </span>
                    <button
                      aria-label={streaming ? 'Generating response' : 'Send message'}
                      className="send-button"
                      disabled={streaming || !question.trim() || !token}
                      type="submit"
                    >
                      {streaming ? <span className="spinner" /> : <Icon name="send" size={16} />}
                    </button>
                  </div>
                </form>
                <p className="disclaimer">DocuChat can make mistakes. Verify important details with the cited source.</p>
              </div>
            </section>

            <aside aria-label="Your documents" className="documents-panel">
              <div className="panel-heading">
                <div><h2>Your sources</h2><p>{documents.length} document{documents.length === 1 ? '' : 's'} in your library</p></div>
                <button
                  aria-label="Refresh documents"
                  className={`icon-button${loadingDocuments ? ' is-loading' : ''}`}
                  disabled={!token || loadingDocuments}
                  onClick={() => refreshDocuments()}
                  title="Refresh documents"
                  type="button"
                >
                  <Icon name="refresh" size={16} />
                </button>
              </div>

              <label className={`upload-zone${uploading ? ' uploading' : ''}`}>
                <input
                  accept=".pdf,.doc,.docx,.txt,.md"
                  disabled={!token || uploading}
                  onChange={handleUpload}
                  ref={fileInputRef}
                  type="file"
                />
                <span className="upload-icon">{uploading ? <span className="spinner" /> : <Icon name="upload" size={17} />}</span>
                <span className="upload-label">{uploading ? 'Indexing document…' : 'Add documents'}</span>
                <span className="upload-hint">PDF, DOCX, TXT, MD</span>
              </label>

              {!token ? (
                <div className="panel-empty">
                  <div className="panel-empty-icon"><Icon name="lock" size={18} /></div>
                  <strong>Connect to view sources</strong>
                  <span>Your private library will appear here.</span>
                  <button className="text-button" onClick={() => setShowSettings(true)} type="button">Connect workspace <span>→</span></button>
                </div>
              ) : loadingDocuments && documents.length === 0 ? (
                <div className="document-loading"><span className="spinner" /> Loading your library…</div>
              ) : documents.length === 0 ? (
                <EmptyDocuments disabled={!token || uploading} onUpload={() => fileInputRef.current?.click()} />
              ) : (
                <>
                  <div className="source-filter-note">
                    {selectedCount === 0
                      ? 'All documents are included in chat'
                      : `${selectedCount} selected for chat`}
                  </div>
                  <div className="document-list">
                    {documents.map((document) => (
                      <DocumentRow
                        document={document}
                        key={document.documentId}
                        onDelete={handleDelete}
                        onSelect={toggleDocument}
                        selected={selectedIds.includes(document.documentId)}
                      />
                    ))}
                  </div>
                  <button className="view-all-button" onClick={() => setActiveView('documents')} type="button">
                    View all documents <span>→</span>
                  </button>
                </>
              )}
              <div className="panel-footer">
                <span className="panel-footer-icon"><Icon name="spark" size={13} /></span>
                Answers grounded in your files
              </div>
            </aside>
          </div>
        ) : (
          <section aria-label="Document library" className="library-view">
            <div className="library-header">
              <div>
                <div className="eyebrow"><span className="eyebrow-dot" /> YOUR KNOWLEDGE BASE</div>
                <h1>Document library<span className="heading-period">.</span></h1>
                <p>Manage your sources and choose which documents to include in a conversation.</p>
              </div>
              <label className={`button button-primary library-upload${uploading ? ' uploading' : ''}`}>
                <input
                  accept=".pdf,.doc,.docx,.txt,.md"
                  disabled={!token || uploading}
                  onChange={handleUpload}
                  type="file"
                />
                {uploading ? <span className="spinner" /> : <Icon name="upload" size={16} />}
                {uploading ? 'Indexing…' : 'Upload document'}
              </label>
            </div>
            <div className="library-toolbar">
              <div><strong>{documents.length}</strong> {documents.length === 1 ? 'document' : 'documents'}</div>
              <button className="button button-quiet button-small" disabled={!token || loadingDocuments} onClick={() => refreshDocuments()} type="button">
                <Icon name="refresh" size={15} /> Refresh
              </button>
            </div>
            {!token ? (
              <div className="library-empty">
                <div className="empty-icon"><Icon name="lock" size={21} /></div>
                <strong>Connect your workspace</strong>
                <p>Paste a valid bearer token to see your documents.</p>
                <button className="button button-primary button-small" onClick={() => setShowSettings(true)} type="button">Connect workspace</button>
              </div>
            ) : loadingDocuments && documents.length === 0 ? (
              <div className="library-empty"><span className="spinner" /><p>Loading your documents…</p></div>
            ) : documents.length === 0 ? (
              <div className="library-empty">
                <div className="empty-icon"><Icon name="files" size={21} /></div>
                <strong>No documents yet</strong>
                <p>Upload a PDF, Word document, text file, or Markdown file to get started.</p>
              </div>
            ) : (
              <div className="library-grid">
                {documents.map((document) => (
                  <article className="library-card" key={document.documentId}>
                    <div className="library-card-top">
                      <span className={`file-type file-type-${getDocumentKind(document).toLowerCase()}`}>
                        {getDocumentKind(document)}
                      </span>
                      <button aria-label={`Delete ${document.fileName}`} className="icon-button" onClick={() => handleDelete(document)} type="button">
                        <Icon name="trash" size={16} />
                      </button>
                    </div>
                    <h3 title={document.fileName}>{document.fileName}</h3>
                    <div className="library-card-meta">
                      <span>{formatBytes(document.fileSizeBytes)}</span><span>{document.pageCount || 1} pages</span>
                    </div>
                    <div className="library-card-bottom">
                      <span className={`document-status status-${document.status?.toLowerCase() || 'unknown'}`}>
                        <span className="status-dot" />{document.status?.toLowerCase() || 'unknown'}
                      </span>
                      <span>{formatDate(document.uploadedAt)}</span>
                    </div>
                  </article>
                ))}
              </div>
            )}
            <div className="library-tip"><Icon name="spark" size={15} /> Tip: select documents in the chat panel to narrow your questions to specific sources.</div>
          </section>
        )}

        <footer className="app-footer"><span>DOCUCHAT AI</span><span>Built for thoughtful answers</span></footer>
      </main>

      {showSettings && (
        <SettingsDialog
          busy={loadingDocuments}
          connected={Boolean(token)}
          draft={tokenDraft}
          onClose={() => setShowSettings(false)}
          onConnect={handleConnect}
          onDisconnect={handleDisconnect}
          setDraft={setTokenDraft}
        />
      )}
    </div>
  );
}
