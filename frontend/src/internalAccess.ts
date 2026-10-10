export const internalRoles = ['ADMIN', 'MANAGER', 'SUPPORT', 'WAREHOUSE', 'DELIVERY'] as const

export function isInternalRole(role: string) {
  return internalRoles.some((candidate) => candidate === role)
}

export const internalRoleNames: Record<string, string> = {
  ADMIN: 'Quản trị viên',
  MANAGER: 'Quản lý',
  SUPPORT: 'Nhân viên hỗ trợ',
  WAREHOUSE: 'Nhân viên kho',
  DELIVERY: 'Nhân viên giao hàng',
}
