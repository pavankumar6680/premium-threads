import clsx from 'clsx';
import { formatMoney } from '../../../lib/format';
import type { ColorOption, ProductCard } from '../types';

/** Current price, the original price struck through when there is a real reduction, and the saving. */
export function Price({ price, compareAtPrice, size = 'md' }: { price: number; compareAtPrice?: number | null; size?: 'md' | 'lg' }) {
  const onSale = compareAtPrice != null && compareAtPrice > price;
  const percent = onSale ? Math.round(((compareAtPrice - price) / compareAtPrice) * 100) : 0;
  return (
    <p className={clsx('flex flex-wrap items-baseline gap-x-2', size === 'lg' ? 'text-3xl' : 'text-base')}>
      <span className={clsx('font-bold', onSale && 'text-brand-600')}>{formatMoney(price)}</span>
      {onSale && (
        <>
          <span className={clsx('text-slate-400 line-through', size === 'lg' ? 'text-lg' : 'text-sm')}>
            <span className="sr-only">was </span>
            {formatMoney(compareAtPrice)}
          </span>
          <span className={clsx('font-semibold text-brand-600', size === 'lg' ? 'text-base' : 'text-xs')}>{percent}% off</span>
        </>
      )}
    </p>
  );
}

/** New Arrival, Trending and Sale badges. */
export function ProductBadges({ product, className }: { product: Pick<ProductCard, 'newArrival' | 'trending' | 'compareAtPrice' | 'price'>; className?: string }) {
  const onSale = product.compareAtPrice != null && product.compareAtPrice > product.price;
  if (!product.newArrival && !product.trending && !onSale) return null;
  return (
    <div className={clsx('flex flex-col items-start gap-1', className)}>
      {onSale && <Badge className="bg-brand-500 text-white">Sale</Badge>}
      {product.newArrival && <Badge className="bg-slate-900 text-white">New arrival</Badge>}
      {product.trending && <Badge className="bg-violet-100 text-violet-800">Trending</Badge>}
    </div>
  );
}

function Badge({ className, children }: { className: string; children: string }) {
  return <span className={clsx('rounded-full px-2.5 py-1 text-[11px] font-bold uppercase tracking-wide shadow-sm', className)}>{children}</span>;
}

/** Up to {@code max} color swatches and a "+n" overflow. */
export function ColorDots({ colors, max = 5, className }: { colors: ColorOption[]; max?: number; className?: string }) {
  if (colors.length === 0) return null;
  const shown = colors.slice(0, max);
  return (
    <div className={clsx('flex items-center gap-1.5', className)} aria-label={`Colors: ${colors.map((c) => c.name).join(', ')}`}>
      {shown.map((c) => (
        <span key={c.name} title={c.name} className="size-3.5 rounded-full ring-1 ring-black/15" style={{ backgroundColor: c.hex ?? '#ddd' }} />
      ))}
      {colors.length > max && <span className="text-xs text-slate-500">+{colors.length - max}</span>}
    </div>
  );
}

/** "XS–XL", "28–38", "One size". */
export function sizeRange(sizes: string[]): string {
  if (sizes.length === 0 || (sizes.length === 1 && sizes[0] === 'One size')) return 'One size';
  return sizes.length === 1 ? sizes[0] : `${sizes[0]}–${sizes[sizes.length - 1]}`;
}

/** Sold in a single option: can be added to the cart without choosing a size or color. */
export function isSingleOption(product: Pick<ProductCard, 'sizes' | 'colors'>): boolean {
  return product.sizes.length <= 1 && product.colors.length <= 1;
}
