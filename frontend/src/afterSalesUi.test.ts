import { describe, expect, it } from 'vitest'
import {
  inspectionResultLabel,
  refundMethodLabel,
  returnStatusLabel,
  ticketStatusLabel,
} from './afterSalesUi'

describe('after-sales UI labels', () => {
  it('translates support statuses into Vietnamese', () => {
    expect(ticketStatusLabel('IN_PROGRESS')).toBe('Đang xử lý')
    expect(ticketStatusLabel('RESOLVED')).toBe('Đã giải quyết')
  })

  it('translates return and refund values into user-facing text', () => {
    expect(returnStatusLabel('REFUND_PENDING')).toBe('Chờ hoàn tiền')
    expect(refundMethodLabel('SIMULATED_QR')).toBe('Hoàn tiền qua QR mô phỏng')
    expect(inspectionResultLabel('NOT_ELIGIBLE')).toBe('Không đủ điều kiện')
  })

  it('uses a safe fallback instead of exposing an unknown backend code', () => {
    expect(ticketStatusLabel('NEW_INTERNAL_STATUS')).toBe('Chưa xác định')
    expect(returnStatusLabel('NEW_INTERNAL_STATUS')).toBe('Chưa xác định')
  })
})
