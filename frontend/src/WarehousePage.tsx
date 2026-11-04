
import { useCallback, useEffect, useState } from 'react'
import StaffLayout, { type StaffTab } from './StaffLayout'
import DailyReportPanel from './DailyReportPanel'
import {
  errorMessage, listPackingOrders, packOrder,
  type PackingOrder, type PageResponse,
} from './api/warehouseDelivery'
import { inspectReturn, listWarehouseReturns, type ReturnRequest } from './api/afterSales'
import { Pager, formatDateTime, formatVnd } from './logisticsUi'
import './logistics.css'

type Tab = 'orders' | 'returns' | 'report'

const NAV: StaffTab<Tab>[] = [
  { id: 'orders', label: 'Đóng gói' },
  { id: 'returns', label: 'Kiểm định hoàn trả' },
  { id: 'report', label: 'Báo cáo ngày' },
]

const PAGE_SIZE = 20

export default function WarehousePage() {
  const [tab, setTab] = useState<Tab>('orders')

  return (
    <StaffLayout
      eyebrow="Vận hành"
      title="Hàng đợi kho"
      roleName="Nhân viên kho"
      tabs={NAV}
      active={tab}
      onNavigate={setTab}
    >
      {tab === 'orders'
        ? <PackingPanel />
        : tab === 'returns'
          ? <ReturnInspectionPanel />
          : <DailyReportPanel team="warehouse" />}
    </StaffLayout>
  )
}

function PackingPanel() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<PackingOrder> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setData(await listPackingOrders(page, PAGE_SIZE))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    void load()
  }, [load])

  async function pack(order: PackingOrder) {
    if (!window.confirm(`Đóng gói đơn ${order.orderCode}? Hệ thống sẽ trừ tồn kho và tạo vận đơn.`)) return

    setBusyId(order.id)
    setError('')
    setNotice('')

    try {
      const result = await packOrder(order.id)
      setNotice(`Đã đóng gói ${order.orderCode}. Mã vận đơn: ${result.shipment.trackingNumber ?? '—'}.`)
      await load()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section>
      {error && <div className="lg-msg err" role="alert">{error}</div>}
      {notice && <div className="lg-msg ok" role="status">{notice}</div>}

      <div className="lg-tablewrap">
        <table className="lg-table">
          <thead>
            <tr>
              <th>Đơn hàng</th>
              <th>Người nhận</th>
              <th>Sản phẩm</th>
              <th>Thanh toán</th>
              <th>Trạng thái</th>
              <th className="num">Tổng tiền</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((o) => (
              <tr key={o.id}>
                <td>{o.orderCode}<small>{formatDateTime(o.createdAt)}</small></td>
                <td>
                  {o.recipientName}
                  <small>{o.recipientPhone}</small>
                  <small>{o.deliveryAddress}</small>
                </td>
                <td>
                  {o.items.map((i) => (
                    <div key={`${o.id}-${i.productId}-${i.sku}`}>
                      {i.productName} × {i.quantity}
                      <small>{i.sku}</small>
                    </div>
                  ))}
                </td>
                <td>
                  {o.paymentMethod === 'COD' ? 'COD' : 'QR'} · {o.paymentStatus === 'PAID' ? 'đã thanh toán' : 'chưa thanh toán'}
                </td>
                <td>
                  <span className={`lg-chip ${o.status === 'PROCESSING' ? 'lg-warn' : 'lg-info'}`}>
                    {o.status === 'PROCESSING' ? 'Đang xử lý' : 'Đã xác nhận'}
                  </span>
                </td>
                <td className="num">{formatVnd(o.totalAmount)}</td>
                <td>
                  <button
                    type="button"
                    className="lg-btn primary"
                    disabled={busyId === o.id}
                    onClick={() => void pack(o)}
                  >
                    {busyId === o.id ? 'Đang xử lý…' : 'Đóng gói'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {!loading && data?.items.length === 0 && (
          <div className="lg-empty">Không có đơn nào đang chờ đóng gói.</div>
        )}
        {loading && <div className="lg-empty">Đang tải…</div>}
      </div>

      {data && (
        <Pager
          page={page}
          size={PAGE_SIZE}
          total={data.total}
          onPage={setPage}
        />
      )}
    </section>
  )
}

function ReturnInspectionPanel() {
  const [rows, setRows] = useState<ReturnRequest[]>([])
  const [drafts, setDrafts] = useState<
    Record<number, { received: string; restock: string; note: string }>
  >({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<number | null>(null)

  const load = useCallback(async () => {
    try {
      setRows((await listWarehouseReturns()).items)
      setError('')
    } catch (e) {
      setError(errorMessage(e))
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function submit(row: ReturnRequest) {
    setBusy(row.id)
    setError('')

    try {
      const items = row.items.map((item) => {
        const draft = drafts[item.id] ?? {
          received: String(item.requestedQuantity),
          restock: String(item.requestedQuantity),
          note: 'Hàng nguyên vẹn',
        }

        return {
          returnRequestItemId: item.id,
          receivedQuantity: Number(draft.received),
          restockQuantity: Number(draft.restock),
          conditionNote: draft.note,
        }
      })

      if (
        items.some((i) =>
          !Number.isInteger(i.receivedQuantity) ||
          !Number.isInteger(i.restockQuantity) ||
          i.receivedQuantity < 0 ||
          i.restockQuantity < 0 ||
          i.restockQuantity > i.receivedQuantity ||
          !i.conditionNote.trim()
        )
      ) {
        setError('Kiểm tra lại số lượng nhận, số lượng nhập kho và ghi chú.')
        return
      }

      await inspectReturn(row.id, items)
      await load()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(null)
    }
  }

  return (
    <section>
      {error && <div className="lg-msg err" role="alert">{error}</div>}

      <div className="lg-toolbar">
        <span>{rows.length} yêu cầu được phép nhận hàng trả</span>
        <button className="lg-btn ghost" onClick={() => void load()}>
          Làm mới
        </button>
      </div>

      {rows.map((row) => (
        <article className="lg-panel" key={row.id}>
          <div className="lg-toolbar">
            <strong>#{row.id} · {row.orderCode}</strong>
            <span>{row.customerName}</span>
          </div>

          <p>{row.reason}</p>

          {row.items.map((item) => {
            const draft = drafts[item.id] ?? {
              received: String(item.requestedQuantity),
              restock: String(item.requestedQuantity),
              note: 'Hàng nguyên vẹn',
            }

            const update = (patch: Partial<typeof draft>) =>
              setDrafts({
                ...drafts,
                [item.id]: { ...draft, ...patch },
              })

            return (
              <div className="lg-panel" key={item.id}>
                <strong>
                  {item.productName} ({item.sku}) · yêu cầu {item.requestedQuantity}
                </strong>

                <div className="lg-toolbar">
                  <label>
                    Đã nhận
                    <input
                      type="number"
                      min="0"
                      max={item.requestedQuantity}
                      value={draft.received}
                      onChange={(e) => update({ received: e.target.value })}
                    />
                  </label>

                  <label>
                    Nhập kho
                    <input
                      type="number"
                      min="0"
                      max={item.requestedQuantity}
                      value={draft.restock}
                      onChange={(e) => update({ restock: e.target.value })}
                    />
                  </label>
                </div>

                <div className="lg-field">
                  <label>Ghi chú tình trạng</label>
                  <textarea
                    rows={2}
                    value={draft.note}
                    onChange={(e) => update({ note: e.target.value })}
                  />
                </div>
              </div>
            )
          })}

          <button
            className="lg-btn primary"
            disabled={busy === row.id}
            onClick={() => void submit(row)}
          >
            {busy === row.id ? 'Đang lưu…' : 'Lưu kết quả kiểm tra'}
          </button>
        </article>
      ))}

      {rows.length === 0 && (
        <div className="lg-empty">Không có hàng trả đang chờ kiểm tra.</div>
      )}
    </section>
  )
}
