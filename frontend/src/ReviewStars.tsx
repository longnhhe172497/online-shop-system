import './reviews.css'

const LEVELS = [1, 2, 3, 4, 5]
const ratingWords = ['', 'Rất tệ', 'Chưa tốt', 'Bình thường', 'Tốt', 'Tuyệt vời']

/** Read-only stars, e.g. ★★★★☆. Screen readers hear "4 trên 5 sao". */
export function Stars({ rating, className = '' }: { rating: number; className?: string }) {
  const rounded = Math.round(rating)
  return <span className={`rv-stars ${className}`.trim()} role="img" aria-label={`${rating} trên 5 sao`}>
    {LEVELS.map((level) => <span key={level} aria-hidden="true" className={level <= rounded ? 'on' : ''}>★</span>)}
  </span>
}

/** Star input built from radio buttons, so it works with the keyboard and screen readers. */
export function StarPicker({ value, onChange, name, disabled = false }: {
  value: number; onChange: (rating: number) => void; name: string; disabled?: boolean }) {
  return <fieldset className="rv-picker" disabled={disabled}>
    <legend>Chấm điểm</legend>
    <div className="rv-picker-row">
      {LEVELS.map((level) => <label key={level} className={level <= value ? 'on' : ''} title={ratingWords[level]}>
        <input type="radio" name={name} value={level} checked={value === level} aria-label={`${level} sao`}
          onChange={() => onChange(level)} />
        <span aria-hidden="true">★</span>
      </label>)}
      <span className="rv-picker-word" aria-live="polite">{value > 0 ? ratingWords[value] : 'Chọn số sao'}</span>
    </div>
  </fieldset>
}
