import clsx from 'clsx';
import { Ruler } from 'lucide-react';
import { useState } from 'react';
import { Dialog } from '../../../components/ui/Dialog';
import type { ProductDetail, Variant } from '../types';

/** Size × color selection state for a product, with the stock of the chosen option. */
export function useVariantSelection(variants: Variant[], preferredColor?: string | null) {
  const colors = [...new Map(variants.map((v) => [v.color, v])).values()];
  const sizes = [...new Set(variants.map((v) => v.size))];
  const singleOption = variants.length <= 1;
  const [color, setColor] = useState<string | undefined>(() => {
    const preferred = preferredColor && colors.find((c) => c.color === preferredColor);
    if (preferred) return preferred.color;
    return (colors.find((c) => variants.some((v) => v.color === c.color && v.availableQuantity > 0)) ?? colors[0])?.color;
  });
  const [size, setSize] = useState<string | null>(sizes.length === 1 ? sizes[0] : null);
  const variant = singleOption ? variants[0] : variants.find((v) => v.color === color && v.size === size);
  const colorHex = colors.find((c) => c.color === color)?.colorHex ?? null;
  return {
    colors,
    sizes,
    singleOption,
    color,
    setColor,
    size,
    setSize,
    variant,
    colorHex,
    stock: variant?.availableQuantity ?? 0,
    needsSize: !singleOption && !variant,
  };
}

export type VariantSelection = ReturnType<typeof useVariantSelection>;

export function VariantPicker({ selection, variants, sizeScale }: {
  selection: VariantSelection;
  variants: Variant[];
  sizeScale: ProductDetail['sizeScale'];
}) {
  const [guideOpen, setGuideOpen] = useState(false);
  const { colors, sizes, color, setColor, size, setSize, singleOption } = selection;
  if (singleOption) return null;
  const showColors = !(colors.length === 1 && colors[0].color === 'Standard');
  return (
    <div className="space-y-5">
      {showColors && (
        <fieldset>
          <legend className="text-sm font-semibold">
            Color: <span className="font-normal text-slate-600">{color}</span>
          </legend>
          <div className="mt-2.5 flex flex-wrap gap-2.5">
            {colors.map((c) => {
              const anyStock = variants.some((v) => v.color === c.color && v.availableQuantity > 0);
              return (
                <button
                  key={c.color}
                  type="button"
                  aria-pressed={c.color === color}
                  aria-label={anyStock ? c.color : `${c.color} (sold out)`}
                  title={c.color}
                  onClick={() => setColor(c.color)}
                  className={clsx(
                    'relative size-10 rounded-full ring-1 ring-black/15 ring-offset-2 transition-shadow',
                    c.color === color ? 'ring-2 ring-slate-900' : 'hover:ring-slate-400',
                    !anyStock && 'opacity-40',
                  )}
                  style={{ backgroundColor: c.colorHex ?? '#ddd' }}
                />
              );
            })}
          </div>
        </fieldset>
      )}
      {sizes.length > 1 && (
        <fieldset>
          <div className="flex items-center justify-between">
            <legend className="text-sm font-semibold">
              Size{size ? <span className="font-normal text-slate-600">: {size}</span> : null}
            </legend>
            {sizeScale !== 'ONE_SIZE' && (
              <button type="button" onClick={() => setGuideOpen(true)} className="inline-flex items-center gap-1 text-sm font-semibold text-violet-700 hover:underline">
                <Ruler className="size-4" aria-hidden="true" /> Size guide
              </button>
            )}
          </div>
          <div className="mt-2.5 flex flex-wrap gap-2">
            {sizes.map((s) => {
              const option = variants.find((v) => v.color === color && v.size === s);
              const soldOut = !option || option.availableQuantity === 0;
              const low = option && option.availableQuantity > 0 && option.availableQuantity <= 3;
              return (
                <button
                  key={s}
                  type="button"
                  aria-pressed={s === size}
                  aria-label={soldOut ? `${s} (sold out)` : low ? `${s} (only ${option.availableQuantity} left)` : s}
                  disabled={soldOut}
                  onClick={() => setSize(s)}
                  className={clsx(
                    'relative h-11 min-w-14 rounded-xl border px-3 text-sm font-semibold transition-colors',
                    s === size ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-300 bg-white hover:border-slate-900',
                    'disabled:cursor-not-allowed disabled:border-slate-200 disabled:bg-slate-50 disabled:text-slate-300 disabled:line-through',
                  )}
                >
                  {s}
                  {low && s !== size && <span className="absolute -right-1 -top-1 size-2.5 rounded-full bg-brand-500" aria-hidden="true" />}
                </button>
              );
            })}
          </div>
        </fieldset>
      )}
      <SizeGuide open={guideOpen} onClose={() => setGuideOpen(false)} scale={sizeScale} />
    </div>
  );
}

const ALPHA = [
  ['XS', '32–34', '81–86', '26–28', '66–71'],
  ['S', '35–37', '89–94', '29–31', '74–79'],
  ['M', '38–40', '97–102', '32–34', '81–86'],
  ['L', '41–43', '104–109', '35–37', '89–94'],
  ['XL', '44–46', '112–117', '38–40', '97–102'],
  ['XXL', '47–49', '119–124', '41–43', '104–109'],
];

export function SizeGuide({ open, onClose, scale }: { open: boolean; onClose: () => void; scale: ProductDetail['sizeScale'] }) {
  const cell = 'px-3 py-2 text-left';
  return (
    <Dialog open={open} onClose={onClose} title="Size guide" size="lg">
      {scale === 'WAIST' ? (
        <>
          <p className="text-sm text-slate-600">Jeans and trousers are sized by waist in inches. Measure around your natural waist, where you normally wear your trousers.</p>
          <table className="mt-4 w-full text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr><th className={cell}>Size</th><th className={cell}>Waist (in)</th><th className={cell}>Waist (cm)</th><th className={cell}>Inseam (in)</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {[24, 26, 28, 30, 32, 34, 36, 38].map((w) => (
                <tr key={w}><td className={cell + ' font-semibold'}>{w}</td><td className={cell}>{w}</td><td className={cell}>{Math.round(w * 2.54)}</td><td className={cell}>{w < 30 ? 30 : 32}</td></tr>
              ))}
            </tbody>
          </table>
        </>
      ) : (
        <>
          <p className="text-sm text-slate-600">Tops, dresses and outerwear. Measure around the fullest part of your chest and your natural waist. Between sizes? Size up for a relaxed fit.</p>
          <div className="overflow-x-auto">
            <table className="mt-4 w-full min-w-[28rem] text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr><th className={cell}>Size</th><th className={cell}>Chest (in)</th><th className={cell}>Chest (cm)</th><th className={cell}>Waist (in)</th><th className={cell}>Waist (cm)</th></tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {ALPHA.map(([s, ...row]) => (
                  <tr key={s}><td className={cell + ' font-semibold'}>{s}</td>{row.map((v, i) => <td key={i} className={cell}>{v}</td>)}</tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
      <p className="mt-4 text-xs text-slate-500">General guidance for this demo store; fits vary by brand and style.</p>
    </Dialog>
  );
}
