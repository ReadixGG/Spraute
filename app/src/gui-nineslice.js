/**
 * 9-slice — порт логики SprauteScriptScreen.renderNineSlice (1.20.1).
 * borders: неизменяемая ширина краёв в пикселях исходной текстуры.
 * sliceScale: множитель толщины края на экране (< 1 — тоньше рамка).
 *
 * Углы и центр — масштабируются; горизонтальные/вертикальные края
 * повторяются плитками (как в игре), а не растягиваются целиком.
 */

export function computeNineSliceLayout(texW, texH, destW, destH, borders, sliceScale) {
  const tw = Math.max(1, texW | 0);
  const th = Math.max(1, texH | 0);
  const w = Math.max(1, destW | 0);
  const h = Math.max(1, destH | 0);
  const b = Math.min(Math.max(0, borders | 0), Math.min(tw, th) / 2);
  if (b <= 0) return null;
  const scale = sliceScale <= 0 ? 1 : sliceScale;
  let bs = Math.max(1, Math.round(b * scale));
  bs = Math.min(bs, Math.min(w, h) / 2);
  if (bs <= 0) bs = 1;
  return {
    b,
    bs,
    midW: w - bs * 2,
    midH: h - bs * 2,
    srcMidW: tw - b * 2,
    srcMidH: th - b * 2,
    tw,
    th,
    destW: w,
    destH: h,
  };
}

function blitStretch(ctx, img, dx, dy, dw, dh, su, sv, sw, sh) {
  if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) return;
  ctx.drawImage(img, su, sv, sw, sh, dx, dy, dw, dh);
}

/** Повторить фрагмент sw×sh по горизонтали (плитка destTileW). */
function tileHorizontal(ctx, img, x, y, w, h, su, sv, sw, sh, destTileW) {
  const tileW = Math.max(1, destTileW | 0);
  let cx = x;
  const end = x + w;
  while (cx < end) {
    const dw = Math.min(tileW, end - cx);
    const srcW = Math.max(1, Math.round(dw * (sw / tileW)));
    blitStretch(ctx, img, cx, y, dw, h, su, sv, srcW, sh);
    cx += tileW;
  }
}

/** Повторить фрагмент sw×sh по вертикали (плитка destTileH). */
function tileVertical(ctx, img, x, y, w, h, su, sv, sw, sh, destTileH) {
  const tileH = Math.max(1, destTileH | 0);
  let cy = y;
  const end = y + h;
  while (cy < end) {
    const dh = Math.min(tileH, end - cy);
    const srcH = Math.max(1, Math.round(dh * (sh / tileH)));
    blitStretch(ctx, img, x, cy, w, dh, su, sv, sw, srcH);
    cy += tileH;
  }
}

/** Нарисовать 9 регионов на canvas (координаты в px назначения). */
export function drawNineSlice(ctx, img, destX, destY, destW, destH, borders, sliceScale) {
  const tw = img.naturalWidth || img.width;
  const th = img.naturalHeight || img.height;
  const layout = computeNineSliceLayout(tw, th, destW, destH, borders, sliceScale);
  if (!layout) {
    ctx.drawImage(img, destX, destY, destW, destH);
    return;
  }
  const { b, bs, midW, midH, srcMidW, srcMidH } = layout;
  const ix = destX;
  const iy = destY;
  const w = destW;
  const h = destH;

  // Углы — один раз, масштаб b×b → bs×bs.
  blitStretch(ctx, img, ix, iy, bs, bs, 0, 0, b, b);
  blitStretch(ctx, img, ix + w - bs, iy, bs, bs, tw - b, 0, b, b);
  blitStretch(ctx, img, ix, iy + h - bs, bs, bs, 0, th - b, b, b);
  blitStretch(ctx, img, ix + w - bs, iy + h - bs, bs, bs, tw - b, th - b, b, b);

  // Края — тайлинг вдоль длинной стороны.
  if (midW > 0 && srcMidW > 0) {
    tileHorizontal(ctx, img, ix + bs, iy, midW, bs, b, 0, b, b, bs);
    tileHorizontal(ctx, img, ix + bs, iy + h - bs, midW, bs, b, th - b, b, b, bs);
  }
  if (midH > 0 && srcMidH > 0) {
    tileVertical(ctx, img, ix, iy + bs, bs, midH, 0, b, b, b, bs);
    tileVertical(ctx, img, ix + w - bs, iy + bs, bs, midH, tw - b, b, b, b, bs);
  }

  // Центр — растяжение.
  if (midW > 0 && midH > 0 && srcMidW > 0 && srcMidH > 0) {
    blitStretch(ctx, img, ix + bs, iy + bs, midW, midH, b, b, srcMidW, srcMidH);
  }
}

/** Пиксельный масштаб превью текстуры (целое число, макс. сторона maxSide). */
export function texturePreviewScale(texW, texH, maxSide = 168) {
  const tw = Math.max(1, texW | 0);
  const th = Math.max(1, texH | 0);
  const s = Math.max(1, Math.floor(Math.min(maxSide / tw, maxSide / th)));
  return { scale: s, w: tw * s, h: th * s, tw, th };
}
