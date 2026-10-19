const vnd = new Intl.NumberFormat('vi-VN')

/** 1290000 -> "1.290.000 ₫" */
export function formatVnd(amount: number): string {
  return `${vnd.format(amount)} ₫`
}