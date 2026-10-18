import clsx from 'clsx';
import { Heart } from 'lucide-react';
import { useWishlistToggle } from '../../wishlist/api';

export function WishlistButton({ productId, productName, className, withLabel = false }: {
  productId: number;
  productName: string;
  className?: string;
  withLabel?: boolean;
}) {
  const { saved, toggle, isPending } = useWishlistToggle(productId);
  return (
    <button
      type="button"
      onClick={toggle}
      disabled={isPending}
      aria-pressed={saved}
      aria-label={withLabel ? undefined : saved ? `Remove ${productName} from wishlist` : `Save ${productName} to wishlist`}
      className={clsx(
        'inline-flex items-center justify-center gap-2 rounded-full transition-colors disabled:opacity-60',
        withLabel ? 'h-12 px-5 font-semibold ring-1 ring-slate-300 hover:bg-slate-50' : 'size-9 bg-white/90 shadow-sm backdrop-blur transition-transform hover:scale-110 hover:bg-white',
        className,
      )}
    >
      <Heart
        className={clsx('size-5', saved ? 'fill-brand-500 text-brand-500' : 'text-slate-700')}
        aria-hidden="true"
      />
      {withLabel && (saved ? 'Saved' : 'Save for later')}
    </button>
  );
}
