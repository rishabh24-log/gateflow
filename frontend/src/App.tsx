import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import {
  api,
  clearToken,
  getToken,
  setToken,
  type ApiKeyRow,
  type Metrics,
  type RequestRow,
  type RouteRow,
} from "./api";
import "./App.css";

const navigation = [
  "Overview",
  "API Keys",
  "Usage",
  "Routes",
  "Request Logs",
  "API Tester",
  "Settings",
];

function Brand() {
  return (
    <div className="brand">
      <svg className="brand-mark" width="22" height="22" viewBox="0 0 24 24" aria-hidden="true">
        <rect x="3" y="5.5" width="18" height="4.5" rx="2.25" />
        <rect x="3" y="14" width="18" height="4.5" rx="2.25" />
      </svg>
      <span>GateFlow</span>
    </div>
  );
}

function Login({ onReady }: { onReady: () => void }) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("gateflow");
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    try {
      const result = await api<{ token: string }>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      setToken(result.token);
      onReady();
    } catch {
      setError("Login failed. Use admin / gateflow.");
    }
  }

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={submit}>
        <Brand />
        <h1>Sign in</h1>
        <p>Admin dashboard for the GateFlow gateway.</p>
        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error ? <p className="error">{error}</p> : null}
        <button type="submit">Continue</button>
      </form>
    </div>
  );
}

function Overview() {
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [traffic, setTraffic] = useState<number[]>(Array(12).fill(8));

  useEffect(() => {
    api<Metrics>("/api/admin/metrics").then(setMetrics).catch(() => undefined);
    api<number[]>("/api/admin/traffic").then(setTraffic).catch(() => undefined);
  }, []);

  const points = useMemo(() => {
    const width = 100;
    const height = 40;
    return traffic
      .map((value, index) => {
        const x = (index / Math.max(traffic.length - 1, 1)) * width;
        const y = height - (value / 100) * height;
        return `${x},${y}`;
      })
      .join(" ");
  }, [traffic]);

  const cards = [
    { title: "Requests", value: metrics ? String(metrics.totalRequests) : "—" },
    { title: "Success Rate", value: metrics ? `${metrics.successRate}%` : "—" },
    { title: "Avg. Latency", value: metrics ? `${metrics.avgLatencyMs}ms` : "—" },
    { title: "Blocked", value: metrics ? String(metrics.blockedRequests) : "—" },
  ];

  return (
    <>
      <div className="metrics-grid">
        {cards.map((card) => (
          <div className="metric-card" key={card.title}>
            <span>{card.title}</span>
            <strong>{card.value}</strong>
          </div>
        ))}
      </div>
      <section className="panel">
        <h3>API Traffic</h3>
        <div className="chart-wrap">
          <svg className="line-chart" viewBox="0 0 100 40" preserveAspectRatio="none">
            <line className="chart-grid" x1="0" y1="10" x2="100" y2="10" />
            <line className="chart-grid" x1="0" y1="20" x2="100" y2="20" />
            <line className="chart-grid" x1="0" y1="30" x2="100" y2="30" />
            <polyline className="chart-line" fill="none" points={points} />
          </svg>
        </div>
      </section>
    </>
  );
}

function KeysPage() {
  const [rows, setRows] = useState<ApiKeyRow[]>([]);
  const [name, setName] = useState("Partner key");
  const [quota, setQuota] = useState(30);
  const [created, setCreated] = useState("");

  async function refresh() {
    setRows(await api<ApiKeyRow[]>("/api/admin/keys"));
  }

  useEffect(() => {
    refresh().catch(() => undefined);
  }, []);

  async function create(event: FormEvent) {
    event.preventDefault();
    const result = await api<{ key: string }>("/api/admin/keys", {
      method: "POST",
      body: JSON.stringify({ name, quotaPerMinute: quota }),
    });
    setCreated(result.key);
    await refresh();
  }

  async function revoke(id: number) {
    await api(`/api/admin/keys/${id}`, { method: "DELETE" });
    await refresh();
  }

  return (
    <>
      <form className="toolbar" onSubmit={create}>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Key name" />
        <input
          type="number"
          min={1}
          value={quota}
          onChange={(e) => setQuota(Number(e.target.value))}
        />
        <button type="submit">Create key</button>
      </form>
      {created ? <p className="notice">Copy this key now: <code>{created}</code></p> : null}
      <section className="panel">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Prefix</th>
              <th>Quota / min</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td>{row.name}</td>
                <td>{row.prefix}…</td>
                <td>{row.quotaPerMinute}</td>
                <td>{row.revoked ? "Revoked" : "Active"}</td>
                <td>
                  {!row.revoked ? (
                    <button className="link" type="button" onClick={() => revoke(row.id)}>
                      Revoke
                    </button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </>
  );
}

type UsageRow = {
  id: number;
  consumer: string;
  route: string;
  requests: number;
  period: string;
};

function UsagePage() {
  const [rows, setRows] = useState<UsageRow[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api<UsageRow[]>("/api/admin/usage")
      .then(setRows)
      .catch(() => setError("Usage service is not running on port 8082."));
  }, []);

  return (
    <section className="panel">
      <h3>Downstream usage reports</h3>
      {error ? <p className="error">{error}</p> : null}
      <table>
        <thead>
          <tr>
            <th>Consumer</th>
            <th>Route</th>
            <th>Requests</th>
            <th>Period</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              <td>{row.consumer}</td>
              <td>{row.route}</td>
              <td>{row.requests}</td>
              <td>{row.period}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function RoutesPage() {
  const [rows, setRows] = useState<RouteRow[]>([]);

  useEffect(() => {
    api<RouteRow[]>("/api/admin/routes").then(setRows).catch(() => undefined);
  }, []);

  return (
    <section className="panel">
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Match</th>
            <th>Target</th>
            <th>Auth</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.match}>
              <td>{row.name}</td>
              <td>{row.match}</td>
              <td>{row.target}</td>
              <td>{row.auth}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function LogsPage() {
  const [rows, setRows] = useState<RequestRow[]>([]);

  useEffect(() => {
    const load = () => api<RequestRow[]>("/api/admin/requests").then(setRows).catch(() => undefined);
    load();
    const timer = setInterval(load, 4000);
    return () => clearInterval(timer);
  }, []);

  return (
    <section className="panel">
      <table>
        <thead>
          <tr>
            <th>Time</th>
            <th>Method</th>
            <th>Path</th>
            <th>Status</th>
            <th>Latency</th>
            <th>Key</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              <td>{row.time.replace("T", " ").slice(0, 19)}</td>
              <td>{row.method}</td>
              <td>{row.path}</td>
              <td className={`status-${Math.floor(row.status / 100)}xx`}>{row.status}</td>
              <td>{row.latencyMs}ms</td>
              <td>{row.apiKeyPrefix || "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function TesterPage() {
  const [method, setMethod] = useState("GET");
  const [path, setPath] = useState("/usage");
  const [apiKey, setApiKey] = useState("");
  const [body, setBody] = useState('{"consumer":"Partner B","route":"/v1/search","requests":12}');
  const [result, setResult] = useState("Send a request through the gateway.");

  async function send(event: FormEvent) {
    event.preventDefault();
    const response = await fetch(path, {
      method,
      headers: {
        "X-API-Key": apiKey,
        "Content-Type": "application/json",
      },
      body: method === "GET" ? undefined : body,
    });
    const text = await response.text();
    setResult(`${response.status}\n${text}`);
  }

  return (
    <section className="panel">
      <form className="stack" onSubmit={send}>
        <div className="toolbar">
          <select value={method} onChange={(e) => setMethod(e.target.value)}>
            <option>GET</option>
            <option>POST</option>
          </select>
          <input value={path} onChange={(e) => setPath(e.target.value)} />
        </div>
        <input
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
          placeholder="X-API-Key"
        />
        {method !== "GET" ? (
          <textarea value={body} onChange={(e) => setBody(e.target.value)} rows={4} />
        ) : null}
        <button type="submit">Send through GateFlow</button>
      </form>
      <pre className="result">{result}</pre>
    </section>
  );
}

function SettingsPage() {
  const [info, setInfo] = useState<Record<string, string | number>>({});

  useEffect(() => {
    api<Record<string, string | number>>("/api/admin/settings").then(setInfo).catch(() => undefined);
  }, []);

  return (
    <section className="panel">
      <div className="settings-list">
        {Object.entries(info).map(([key, value]) => (
          <div className="service-row" key={key}>
            <span>{key}</span>
            <strong>{String(value)}</strong>
          </div>
        ))}
      </div>
    </section>
  );
}

function App() {
  const [ready, setReady] = useState(Boolean(getToken()));
  const [activePage, setActivePage] = useState("Overview");

  if (!ready) {
    return <Login onReady={() => setReady(true)} />;
  }

  return (
    <div className="app">
      <aside className="sidebar">
        <Brand />
        <nav className="navigation">
          {navigation.map((item) => (
            <button
              key={item}
              className={`nav-item ${activePage === item ? "active" : ""}`}
              onClick={() => setActivePage(item)}
            >
              {item}
            </button>
          ))}
        </nav>
        <button
          className="nav-item logout"
          onClick={() => {
            clearToken();
            setReady(false);
          }}
        >
          Sign out
        </button>
      </aside>

      <main className="main">
        <p className="eyebrow">DASHBOARD</p>
        <h1>{activePage}</h1>
        <p className="subtitle">
          {activePage === "Overview"
            ? "Live metrics from the GateFlow gateway"
            : `${activePage} connected to the backend`}
        </p>
        {activePage === "Overview" ? <Overview /> : null}
        {activePage === "API Keys" ? <KeysPage /> : null}
        {activePage === "Usage" ? <UsagePage /> : null}
        {activePage === "Routes" ? <RoutesPage /> : null}
        {activePage === "Request Logs" ? <LogsPage /> : null}
        {activePage === "API Tester" ? <TesterPage /> : null}
        {activePage === "Settings" ? <SettingsPage /> : null}
      </main>
    </div>
  );
}

export default App;
