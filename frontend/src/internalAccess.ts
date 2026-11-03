export const internalRoles = ['ADMIN', 'MANAGER', 'SUPPORT', 'WAREHOUSE', 'DELIVERY'] as const

export const internalRoleHomes: Record<string, string> = {
  ADMIN: '/admin',
  MANAGER: '/manager',
  SUPPORT: '/support',
  WAREHOUSE: '/warehouse',
  DELIVERY: '/delivery',
}

export function isInternalRole(role: string) {
  return internalRoles.some((candidate) => candidate === role)
}

export function getInternalHome(role: string) {
  return internalRoleHomes[role] ?? '/'
}

export const internalRoleNames: Record<string, string> = {
  ADMIN: 'Quản trị viên',
  MANAGER: 'Quản lý',
  SUPPORT: 'Nhân viên hỗ trợ',
  WAREHOUSE: 'Nhân viên kho',
  DELIVERY: 'Nhân viên giao hàng',
}
