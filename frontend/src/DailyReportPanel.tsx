import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  errorMessage, listRecentDailyReports, submitDailyReport,
  type DailyReport, type DailyReportTeam,
} from './api/warehouseDelivery'
import './logistics.css'

const TIME_AGO: Record<string, string> = { Today: 'Hôm nay', Yesterday: 'Hôm qua' }

export default function DailyReportPanel({ team }: { team: DailyReportTeam }) {
  const [recent, setRecent] = useState<DailyReport[] | null>(null)
  const [content, setContent] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async (prefill: boolean) => {
    try {
      const items = await listRecentDailyReports(team)
      setRecent(items)
      if (prefill) {
        const mine = items.find((r) => r.mine && r.timeAgo === 'Today')
        if (mine) setContent(mine.content)
      }
    } catch (e) { setError(errorMessage(e)) }
  }, [team])
  useEffect(() => { void load(true) }, [load])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!content.trim()) { setError('Nội dung báo cáo không được để trống.'); return }
    setSaving(true)
    setError('')
    setNotice('')
    try {
      await submitDailyReport(team, content.trim())
      setNotice('Đã gửi báo cáo hôm nay.')
      await load(false)
    } catch (e) { setError(errorMessage(e)) }
    finally { setSaving(false) }
  }

  return <section>
    {error && <div className="lg-msg err" role="alert">{error}</div>}
    {notice && <div className="lg-msg ok" role="status">{notice}</div>}
    <div className="lg-split">
      <form className="lg-panel" onSubmit={submit}>
        <h2>Gửi báo cáo hôm nay</h2>
        <textarea className="lg-textarea" rows={9} maxLength={4000} aria-label="Nội dung báo cáo"
          placeholder="Hôm nay bạn đã xử lý những việc gì?" value={content}
          onChange={(e) => setContent(e.target.value)} />
        <button type="submit" className="lg-btn primary" style={{ marginTop: 10 }} disabled={saving}>
          {saving ? 'Đang gửi…' : 'Gửi báo cáo'}</button>
      </form>
      <div className="lg-panel">
        <h2>Báo cáo gần đây</h2>
        {recent?.map((r) => <div className="lg-summary-row" key={r.id}>
          <div><b>{r.staffName}</b><p>{r.content}</p></div>
          <small>{TIME_AGO[r.timeAgo] ?? r.timeAgo}</small>
        </div>)}
        {recent && recent.length === 0 && <p className="lg-muted-text">Chưa có báo cáo nào.</p>}
      </div>
    </div>
  </section>
}
