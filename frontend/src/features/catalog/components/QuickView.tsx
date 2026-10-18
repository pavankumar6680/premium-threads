import { ArrowRight, ShoppingBag } from 'lucide-react';
import { Link } from 'react-router';
import { Alert } from '../../../components/ui/Alert';
import { Button } from '../../../components/ui/Button';
import { Dialog } from '../../../components/ui/Dialog';
import { Spinner } from '../../../components/ui/Spinner';
import { useAddToCart } from '../../cart/api';
import { useProduct } from '../api';
import type { ProductDetail } from '../types';
import { Price } from './Merchandising';
import { ProductImage } from './ProductImage';
import { StockBadge } from './StockBadge';
import { useVariantSelection, VariantPicker } from './VariantPicker';

/** Choose a size and color and add to the cart without leaving the listing. */
export function QuickView({ slug, open, onClose }: { slug: string; open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onClose={onClose} title="Quick view" size="lg" hideTitle>
      {open && <QuickViewBody slug={slug} onAdded={onClose} />}
    </Dialog>
  );
}

function QuickViewBody({ slug, onAdded }: { slug: string; onAdded: () => void }) {
  const { data, isPending, isError } = useProduct(slug);
  if (isPending) return <div className="flex justify-center py-20"><Spinner /></div>;
  if (isError) return <Alert tone="error">This product could not be loaded.</Alert>;
  return <QuickViewContent detail={data} onAdded={onAdded} />;
}

function QuickViewContent({ detail, onAdded }: { detail: ProductDetail; onAdded: () => void }) {
  const { product } = detail;
  const selection = useVariantSelection(detail.variants);
  const { addToCart, isPending } = useAddToCart();
  const photo = detail.images.find((i) => i.color === selection.color) ?? detail.images[0];
  const purchasable = product.active && !!selection.variant && selection.stock > 0;

  return (
    <div className="grid gap-6 sm:grid-cols-2">
      <ProductImage categorySlug={product.categorySlug} imageUrl={photo?.url} colorHex={selection.colorHex} alt={product.name} className="rounded-2xl" />
      <div>
        <p className="text-xs font-bold uppercase tracking-[0.12em] text-slate-500">{product.brand}</p>
        <h3 className="mt-1 font-display text-2xl font-semibold leading-tight">{product.name}</h3>
        <div className="mt-3"><Price price={product.price} compareAtPrice={product.compareAtPrice} size="md" /></div>
        <div className="mt-5">
          <VariantPicker selection={selection} variants={detail.variants} sizeScale={detail.sizeScale} />
        </div>
        {selection.variant && <StockBadge available={selection.stock} active={product.active} className="mt-3 text-sm" />}
        <Button
          className="mt-5 w-full"
          size="lg"
          disabled={!purchasable}
          isLoading={isPending}
          onClick={() => addToCart(product.id, 1, product.name, selection.variant?.id)}
        >
          <ShoppingBag className="size-5" aria-hidden="true" />
          {selection.needsSize ? 'Choose a size' : purchasable ? 'Add to cart' : 'Sold out'}
        </Button>
        <Link to={`/products/${product.slug}`} onClick={onAdded} className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-violet-700 hover:underline">
          Full details <ArrowRight className="size-4" aria-hidden="true" />
        </Link>
      </div>
    </div>
  );
}
