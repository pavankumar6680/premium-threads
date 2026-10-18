import clsx from 'clsx';
import { useProduct } from '../catalog/api';
import type { BundleItem } from '../recommendations/api';

/** "Size M · Navy" with a swatch, or a prompt to choose a size. */
export function ItemVariantLabel({ item }: { item: BundleItem }) {
  if (item.variant) {
    const oneSize = item.variant.size.toLowerCase() === 'one size';
    const showColor = item.variant.color !== 'Standard';
    if (oneSize && !showColor) return null;
    return (
      <p className="mt-1 flex items-center gap-1.5 text-xs text-slate-600">
        {showColor && (
          <span className="size-3 rounded-full ring-1 ring-black/15" style={{ backgroundColor: item.variant.colorHex ?? '#ddd' }} aria-hidden="true" />
        )}
        {!oneSize && <span>Size <span className="font-semibold text-slate-800">{item.variant.size}</span></span>}
        {!oneSize && showColor && <span aria-hidden="true">·</span>}
        {showColor && <span>{item.variant.color}</span>}
      </p>
    );
  }
  if (item.needsSize) {
    return <p className="mt-1 text-xs font-semibold text-violet-700">Choose a size and color for this piece</p>;
  }
  return null;
}

/**
 * Size and color picker for a piece of an editable outfit. Every option of the product is listed; sold-out ones
 * (or ones without enough stock for the quantity) are disabled.
 */
export function ItemVariantSelect({ item, onChoose, disabled }: {
  item: BundleItem;
  onChoose: (variantId: number) => void;
  disabled: boolean;
}) {
  const { data: product } = useProduct(item.slug ?? '');
  const variants = product?.variants ?? [];
  if (!item.slug || variants.length <= 1) return null;
  const label = (size: string, color: string) =>
    [size.toLowerCase() === 'one size' ? null : size, color === 'Standard' ? null : color].filter(Boolean).join(' · ');
  return (
    <label className="text-xs font-medium text-slate-600">
      <span className="sr-only">Size and color for {item.categoryName}</span>
      <select
        value={item.variant?.id ?? ''}
        disabled={disabled}
        onChange={(e) => e.target.value && onChoose(Number(e.target.value))}
        className={clsx(
          'h-8 rounded-lg border bg-white px-2 text-sm text-slate-900',
          item.needsSize ? 'border-violet-400 ring-2 ring-violet-100' : 'border-slate-300',
        )}
      >
        {!item.variant && <option value="">Choose size…</option>}
        {variants.map((v) => {
          const enough = v.availableQuantity >= item.quantity;
          return (
            <option key={v.id} value={v.id} disabled={!enough}>
              {label(v.size, v.color)}
              {v.availableQuantity === 0 ? ' (sold out)' : !enough ? ` (only ${v.availableQuantity} left)` : ''}
            </option>
          );
        })}
      </select>
    </label>
  );
}
