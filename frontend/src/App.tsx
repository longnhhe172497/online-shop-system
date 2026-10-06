import './App.css'

function App() {
  return (
    <main className="app-shell">
      <section className="status-card">
        <p className="eyebrow">OSM Shop</p>
        <h1>Online Shop System</h1>
        <p>The local development environment is ready.</p>
        <dl>
          <div>
            <dt>Frontend</dt>
            <dd>React + TypeScript + Vite</dd>
          </div>
          <div>
            <dt>Backend</dt>
            <dd>Java 21 + Spring Boot</dd>
          </div>
          <div>
            <dt>Database</dt>
            <dd>PostgreSQL</dd>
          </div>
        </dl>
      </section>
    </main>
  )
}

export default App
