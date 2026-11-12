#!/usr/bin/env bash
# DEV ONLY — downloads the free Unsplash photos used by database/seed/dev_catalog.sql into
# frontend/public/images/products/ (900×1200, matching the 3:4 product card). Credits: CREDITS.md there.
# The photos are committed to the repo, so teammates do NOT need to run this; run it only to
# (re)create the files, e.g. after changing a photo in the list below.
#   bash database/seed/download_product_images.sh          (from the repository root)
set -uo pipefail

OUT="frontend/public/images/products"
[ -d frontend/public ] || { echo "Run this from the repository root."; exit 1; }
mkdir -p "$OUT"

# <file name (SKU)> <Unsplash photo id>
PHOTOS=(
  "st-ao-01 photo-1638598123995-03ad0e68011e"
  "st-ao-02 photo-1581655353564-df123a1eb820"
  "st-ao-03 photo-1791391279730-4756da7b4608"
  "st-ao-04 photo-1650091903034-5f3bb37c35d2"
  "st-ao-05 photo-1620799140408-edc6dcb6d633"
  "st-ao-06 photo-1587180785009-0193650317c3"
  "st-qu-01 photo-1650092034396-d12b29642cf5"
  "st-qu-02 photo-1473966968600-fa801b869a1a"
  "st-qu-03 photo-1493357335960-4583bfa6f8d9"
  "st-qu-04 photo-1594633312681-425c7b97ccd1"
  "st-qu-05 photo-1624378439575-d8705ad7ae80"
  "st-qu-06 photo-1715532098035-a343b26eaeaa"
  "st-pk-01 photo-1544816155-12df9643f363"
  "st-pk-02 photo-1568272975497-7441fb78025c"
  "st-pk-03 photo-1624222247344-550fb60583dc"
  "st-pk-04 photo-1590131222139-91ba5992e4ed"
  "st-pk-05 photo-1600857062241-98e5dba7f214"
  "st-pk-06 photo-1508296695146-257a814070b4"
  "st-ak-01 photo-1631037267268-f59b0e7318ec"
  "st-ak-02 photo-1579206464424-7e43a81cadc1"
  "st-ak-05 photo-1610288311735-39b7facbd095"
  "st-ak-06 photo-1506682025334-be4f5a5ac8a1"
  "st-vd-01 photo-1683660107856-cdad7a562d12"
  "st-vd-03 photo-1617383619176-593fe8bfcde4"
  "st-vd-04 photo-1586693231040-e89840e7d805"
  "st-vd-06 photo-1650091903713-158ac1a43948"
)

failed=0
for entry in "${PHOTOS[@]}"; do
  read -r name id <<< "$entry"
  url="https://images.unsplash.com/$id?w=900&h=1200&fit=crop&crop=center&q=80&fm=jpg"
  if curl -fsSL --retry 2 -o "$OUT/$name.jpg" "$url"; then
    printf '  ok    %s.jpg  (%s KB)\n' "$name" "$(( $(wc -c < "$OUT/$name.jpg") / 1024 ))"
  else
    printf '  FAIL  %s.jpg  <- %s\n' "$name" "$url"; rm -f "$OUT/$name.jpg"; failed=$((failed + 1))
  fi
done
echo "Done: $(( ${#PHOTOS[@]} - failed )) downloaded, $failed failed."
[ "$failed" -eq 0 ]
