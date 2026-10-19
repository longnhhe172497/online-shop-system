import { useState } from 'react'

/** Product photo, or a letter placeholder when there is no image or it fails to load. */
export default function ProductImage({ src, alt }: { src: string | null; alt: string }) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) {
    return <div className="shop-image shop-image-placeholder" role="img" aria-label={alt}>
      {alt.charAt(0).toUpperCase()}
    </div>
  }
  return <img className="shop-image" src={src} alt={alt} loading="lazy" onError={() => setFailed(true)} />
}