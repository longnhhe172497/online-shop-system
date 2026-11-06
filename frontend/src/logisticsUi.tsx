import type { ShipmentStatus } from './api/warehouseDelivery'
import { STATUS_LABELS } from './logisticsFormat'

const STATUS_TONE: Record<ShipmentStatus, string> = {
  PENDING_PICKUP: 'warn', PACKED: 'warn', READY_FOR_DISPATCH: 'info', SHIPPING: 'info',
  DELIVERED: 'ok', DELIVERY_FAILED: 'bad', RETURNED_TO_WAREHOUSE: 'muted',
}

export function StatusChip({ status }: { status: ShipmentStatus }) {
  return <span className={`lg-chip lg-${STATUS_TONE[status]}`}>{STATUS_LABELS[status]}</span>
}

export function Pager({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (p: number) => void }) {
  const totalPages = size > 0 ? Math.max(1, Math.ceil((total || 0) / size)) : 1
  return <div className="lg-pager">
    <span>{total || 0} mục · trang {page + 1}/{totalPages}</span>
    <div>
      <button type="button" className="lg-btn ghost" disabled={page <= 0} onClick={() => onPage(page - 1)}>Trước</button>
      <button type="button" className="lg-btn ghost" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Sau</button>
    </div>
  </div>
}
