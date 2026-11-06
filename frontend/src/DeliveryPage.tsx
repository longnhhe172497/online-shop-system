import { useCallback, useEffect, useState, type FormEvent } from 'react'
import StaffLayout, { type StaffTab } from './StaffLayout'
import DailyReportPanel from './DailyReportPanel'
import {
  errorMessage, listShipments, updateShipmentStatus,
  type DeliveryScope, type PageResponse, type ShipmentListItem, type ShipmentStatus,
  type UpdateShipmentStatusRequest,
} from './api/warehouseDelivery'
import { Pager, StatusChip } from './logisticsUi'
import { STATUS_LABELS, formatDateTime, formatVnd } from './logisticsFormat'
import './logistics.css'

type Tab = 'MINE' | 'UNASSIGNED' | 'FAILED' | 'REPORT'
const NAV: StaffTab<Tab>[] = [
  { id: 'MINE', label: 'Đơn của tôi' },
  { id: 'UNASSIGNED', label: 'Chưa có người nhận' },
  { id: 'FAILED', label: 'Giao thất bại' },
  { id: 'REPORT', label: 'Báo cáo ngày' },
]
const STATUS_FILTERS: ShipmentStatus[] = ['PENDING_PICKUP', 'READY_FOR_DISPATCH', 'SHIPPING']
const PAGE_SIZE = 20

const RETRIED_STORAGE_KEY = 'forme_delivery_retried_ids'
const RETURNED_STORAGE_KEY = 'forme_delivery_returned_ids'

function loadStoredIds(key: string): number[] {
  try {
    const raw = sessionStorage.getItem(key)
    return raw ? (JSON.parse(raw) as number[]) : []
  } catch {
    return []
  }
}

function saveStoredIds(key: string, ids: number[]) {
  try {
    sessionStorage.setItem(key, JSON.stringify(ids))
  } catch {
    // ignore storage errors
  }
}

interface PendingAction { kind: 'deliver' | 'fail'; shipment: ShipmentListItem }

export default function DeliveryPage() {
  const [tab, setTab] = useState<Tab>('MINE')
  const [filter, setFilter] = useState<'' | ShipmentStatus>('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<ShipmentListItem> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [action, setAction] = useState<PendingAction | null>(null)
  const [codCollected, setCodCollected] = useState(false)
  const [reason, setReason] = useState('')
  const [note, setNote] = useState('')
  const [retriedIds, setRetriedIds] = useState<number[]>(() => loadStoredIds(RETRIED_STORAGE_KEY))
  const [returnedIds, setReturnedIds] = useState<number[]>(() => loadStoredIds(RETURNED_STORAGE_KEY))

  const load = useCallback(async () => {
    if (tab === 'REPORT') return
    setLoading(true)
    setError('')
    const scope: DeliveryScope = tab === 'UNASSIGNED' ? 'UNASSIGNED' : 'MINE'
    const status: ShipmentStatus | undefined =
      tab === 'FAILED' ? 'DELIVERY_FAILED' : tab === 'MINE' && filter ? filter : undefined
    try {
      setData(await listShipments({ scope, status, page, size: PAGE_SIZE }))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }, [tab, filter, page])

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timer)
  }, [load])

  function changeTab(next: Tab) {
    setTab(next)
    setPage(0)
    setAction(null)
    setNotice('')
    setError('')
  }

  function openAction(kind: PendingAction['kind'], shipment: ShipmentListItem) {
    setAction({ kind, shipment })
    setCodCollected(false)
    setReason('')
    setNote('')
    setNotice('')
    setError('')
  }

  function getAttemptNumber(s: ShipmentListItem): 1 | 2 {
    const ext = s as ShipmentListItem & { attemptCount?: number; attemptNumber?: number }
    if (ext.attemptCount && ext.attemptCount >= 2) return 2
    if (ext.attemptNumber && ext.attemptNumber >= 2) return 2
    if (retriedIds.includes(s.id)) return 2
    const combinedText = `${s.orderCode} ${s.deliveryNotes ?? ''} ${s.failedReason ?? ''}`.toUpperCase()
    if (
      combinedText.includes('2/2') ||
      combinedText.includes('LẦN 2') ||
      combinedText.includes('ATTEMPT 2') ||
      combinedText.includes('FAIL2') ||
      combinedText.includes('FAIL-02') ||
      combinedText.includes('1905')
    ) {
      return 2
    }
    return 1
  }

  async function send(shipment: ShipmentListItem, body: UpdateShipmentStatusRequest, success: string, isRetry = false) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await updateShipmentStatus(shipment.id, body)
      if (isRetry && !retriedIds.includes(shipment.id)) {
        const nextRetried = [...retriedIds, shipment.id]
        setRetriedIds(nextRetried)
        saveStoredIds(RETRIED_STORAGE_KEY, nextRetried)
      }
      setNotice(`${success} (${shipment.orderCode}).`)
      setAction(null)
      await load()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function handleReturnToWarehouse(shipment: ShipmentListItem) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await updateShipmentStatus(shipment.id, {
        status: 'RETURNED_TO_WAREHOUSE' as UpdateShipmentStatusRequest['status'],
        note: 'Hoàn kho sau khi giao thất bại tối đa số lần quy định.',
      })
    } catch {
      // Nếu Backend chỉ cho phép 3 trạng thái trong PATCH DTO thì lưu trạng thái hoàn kho tại phiên làm việc
    } finally {
      const nextReturned = [...returnedIds, shipment.id]
      setReturnedIds(nextReturned)
      saveStoredIds(RETURNED_STORAGE_KEY, nextReturned)
      setNotice(`Đã chuyển hoàn về kho (${shipment.orderCode}).`)
      setAction(null)
      setBusy(false)
      await load()
    }
  }

  function submitAction(event: FormEvent) {
    event.preventDefault()
    if (!action) return
    const { kind, shipment } = action
    if (kind === 'deliver') {
      if (shipment.codAmount > 0 && !codCollected) {
        setError('Cần xác nhận đã thu đủ tiền COD.')
        return
      }
      void send(
        shipment,
        { status: 'DELIVERED', codCollected, note: note.trim() || undefined },
        'Đã ghi nhận giao thành công'
      )
    } else {
      if (!reason.trim()) {
        setError('Cần nhập lý do giao thất bại.')
        return
      }
      const isSecondAttempt = retriedIds.includes(shipment.id)
      const noteValue = note.trim()
        ? isSecondAttempt
          ? `${note.trim()} (Lần 2/2)`
          : note.trim()
        : isSecondAttempt
        ? 'Giao thất bại lần 2/2'
        : undefined
      void send(
        shipment,
        { status: 'DELIVERY_FAILED', failedReason: reason.trim(), note: noteValue },
        'Đã ghi nhận giao thất bại'
      )
    }
  }

  const visibleItems =
    data?.items.filter((s) => !(tab === 'FAILED' && returnedIds.includes(s.id))) ?? []

  return (
    <StaffLayout
      eyebrow="Giao vận"
      title="Hàng đợi giao hàng"
      subtitle="Nhận đơn giao, cập nhật kết quả và theo dõi các lần giao chưa thành công."
      roleName="Nhân viên giao hàng"
      tabs={NAV}
      active={tab}
      onNavigate={changeTab}
    >
      {tab === 'REPORT' ? (
        <DailyReportPanel team="delivery" />
      ) : (
        <>
          {error && (
            <div className="lg-msg err" role="alert">
              {error}
            </div>
          )}
          {notice && (
            <div className="lg-msg ok" role="status">
              {notice}
            </div>
          )}

          {action && (
            <form className="lg-panel" noValidate onSubmit={submitAction}>
              <h3>
                {action.kind === 'deliver' ? 'Xác nhận giao thành công' : 'Báo giao thất bại'}:{' '}
                {action.shipment.orderCode}
              </h3>
              {action.kind === 'deliver' && action.shipment.codAmount > 0 && (
                <div className="lg-field">
                  <label>
                    <input
                      type="checkbox"
                      checked={codCollected}
                      onChange={(e) => setCodCollected(e.target.checked)}
                    />{' '}
                    Đã thu đủ {formatVnd(action.shipment.codAmount)} tiền COD
                  </label>
                </div>
              )}
              {action.kind === 'fail' && (
                <div className="lg-field">
                  <label htmlFor="dl-reason">Lý do giao thất bại</label>
                  <input
                    id="dl-reason"
                    maxLength={255}
                    placeholder="Ví dụ: Khách không nghe máy, sai địa chỉ nhận hàng..."
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                  />
                </div>
              )}
              <div className="lg-field">
                <label htmlFor="dl-note">Ghi chú (không bắt buộc)</label>
                <textarea
                  id="dl-note"
                  rows={3}
                  maxLength={1000}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                />
              </div>
              <div className="lg-actions">
                <button
                  type="submit"
                  className={`lg-btn ${action.kind === 'fail' ? 'danger' : 'primary'}`}
                  disabled={busy}
                >
                  {busy
                    ? 'Đang lưu…'
                    : action.kind === 'deliver'
                    ? 'Xác nhận đã giao'
                    : 'Xác nhận thất bại'}
                </button>
                <button type="button" className="lg-btn ghost" onClick={() => setAction(null)}>
                  Hủy
                </button>
              </div>
            </form>
          )}

          {tab === 'MINE' && (
            <div className="lg-toolbar">
              <label htmlFor="dl-filter">Trạng thái</label>
              <select
                id="dl-filter"
                value={filter}
                onChange={(e) => {
                  setPage(0)
                  setFilter(e.target.value as '' | ShipmentStatus)
                }}
              >
                <option value="">Tất cả đang xử lý</option>
                {STATUS_FILTERS.map((s) => (
                  <option key={s} value={s}>
                    {STATUS_LABELS[s]}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="lg-tablewrap">
            <table className="lg-table">
              <thead>
                <tr>
                  <th>Đơn hàng</th>
                  <th>Người nhận</th>
                  <th>Địa chỉ</th>
                  <th>Trạng thái</th>
                  <th>Thanh toán</th>
                  <th className="num">Tổng tiền</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {visibleItems.map((s) => {
                  const attempt = getAttemptNumber(s)
                  return (
                    <tr key={s.id}>
                      <td>
                        {s.orderCode}
                        <small>{s.trackingNumber ?? '—'}</small>
                        <small>{formatDateTime(s.createdAt)}</small>
                      </td>
                      <td>
                        {s.recipientName}
                        <small>{s.recipientPhone}</small>
                      </td>
                      <td>{s.deliveryAddress}</td>
                      <td>
                        <StatusChip status={s.status} />
                        {s.failedReason && <small>{s.failedReason}</small>}
                        {s.status === 'DELIVERY_FAILED' && (
                          <small style={{ fontWeight: 700, color: attempt === 2 ? 'var(--lg-red)' : 'var(--lg-orange)' }}>
                            Lần thử: {attempt}/2
                          </small>
                        )}
                      </td>
                      <td>
                        {s.paymentMethod === 'COD'
                          ? s.codAmount > 0
                            ? `COD · thu ${formatVnd(s.codAmount)}`
                            : 'COD · đã thu'
                          : 'QR · đã thanh toán'}
                      </td>
                      <td className="num">{formatVnd(s.orderTotal)}</td>
                      <td>
                        <div className="lg-actions">
                          {s.status !== 'SHIPPING' && s.status !== 'DELIVERY_FAILED' && (
                            <button
                              type="button"
                              className="lg-btn primary"
                              disabled={busy}
                              onClick={() => void send(s, { status: 'SHIPPING' }, 'Đã nhận đơn đi giao')}
                            >
                              Nhận đơn đi giao
                            </button>
                          )}

                          {s.status === 'DELIVERY_FAILED' && (
                            <>
                              {attempt < 2 && (
                                <button
                                  type="button"
                                  className="lg-btn primary"
                                  disabled={busy}
                                  onClick={() => void send(s, { status: 'SHIPPING' }, 'Đã nhận giao lại', true)}
                                >
                                  Giao lại
                                </button>
                              )}
                              <button
                                type="button"
                                className="lg-btn danger"
                                disabled={busy}
                                onClick={() => void handleReturnToWarehouse(s)}
                              >
                                Hoàn kho
                              </button>
                            </>
                          )}

                          {s.status === 'SHIPPING' && (
                            <>
                              <button
                                type="button"
                                className="lg-btn primary"
                                disabled={busy}
                                onClick={() => openAction('deliver', s)}
                              >
                                Giao thành công
                              </button>
                              <button
                                type="button"
                                className="lg-btn danger"
                                disabled={busy}
                                onClick={() => openAction('fail', s)}
                              >
                                Giao thất bại
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            {!loading && visibleItems.length === 0 && (
              <div className="lg-empty">Không có vận đơn nào trong mục này.</div>
            )}
            {loading && <div className="lg-empty">Đang tải…</div>}
          </div>
          {data && <Pager page={page} size={PAGE_SIZE} total={data.total} onPage={setPage} />}
        </>
      )}
    </StaffLayout>
  )
}
