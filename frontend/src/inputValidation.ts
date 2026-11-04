export const PERSON_NAME_PATTERN = /^[\p{L}\p{M}]+(?: +[\p{L}\p{M}]+)*$/u

export function personNameError(value: string, label = 'Họ và tên') {
  const normalized = value.trim()
  if (!normalized) return `Vui lòng nhập ${label.toLocaleLowerCase('vi')}.`
  return PERSON_NAME_PATTERN.test(normalized)
    ? '' : `${label} chỉ được chứa chữ cái và khoảng trắng.`
}

export function phoneError(value: string, required: boolean) {
  if (!value) return required ? 'Vui lòng nhập số điện thoại.' : ''
  return /^\d{10}$/.test(value) ? '' : 'Số điện thoại phải gồm đúng 10 chữ số.'
}

export function phoneDigits(value: string) {
  return value.replace(/\D/g, '').slice(0, 10)
}

export function requiredTextError(value: string, label: string) {
  return value.trim() ? '' : `${label} không được để trống.`
}
