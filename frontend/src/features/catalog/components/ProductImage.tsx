import clsx from 'clsx';
import { categoryVisual } from '../categoryVisuals';

/** Shows the product's first image, or an illustrated category tile when the product has no photo. */
export function ProductImage({ categorySlug, imageUrl, alt, size = 'md', className }: {
  categorySlug: string;
  imageUrl?: string | null;
  alt: string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}) {
  if (imageUrl) {
    return <img src={imageUrl} alt={alt} className={clsx('aspect-square w-full rounded-xl object-cover', className)} />;
  }
  const { icon: Icon, tile } = categoryVisual(categorySlug);
  const iconSize = { sm: 'size-7', md: 'size-14', lg: 'size-28' }[size];
  return (
    <div
      role="img"
      aria-label={alt}
      className={clsx('flex aspect-square w-full items-center justify-center rounded-xl bg-gradient-to-br', tile, className)}
    >
      <Icon className={clsx(iconSize, 'opacity-80')} strokeWidth={1.5} aria-hidden="true" />
    </div>
  );
}
