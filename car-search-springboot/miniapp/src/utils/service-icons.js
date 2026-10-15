const BUILTIN = new Set(['car','vin','plate','verified','location','transfer','owner','etc','insurance','search','document','shield'])

export function serviceIconPath(icon) {
  const key=String(icon||'').replace(/^builtin:/,'')
  return BUILTIN.has(key) ? `/static/icons/services/${key}.svg` : ''
}

export function uploadedServiceIcon(icon, apiBaseUrl) {
  const filename=String(icon||'')
  return /^[a-f0-9]{32}\.(?:png|jpg|webp)$/.test(filename) ? `${apiBaseUrl}/service-icon/${filename}` : ''
}

export function serviceIcon(icon, apiBaseUrl) {
  return uploadedServiceIcon(icon,apiBaseUrl)||serviceIconPath(icon)||'/static/icons/services/car.svg'
}
