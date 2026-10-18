import {
  Backpack,
  Gem,
  Glasses,
  Scissors,
  Shirt,
  ShoppingBag,
  Sparkles,
  Tag,
  Wallet,
  Watch,
  type LucideIcon,
} from 'lucide-react';

type Visual = { icon: LucideIcon; tile: string; accent: string };

// Small icon tiles used in lists (goal items, templates). Product pictures use GarmentArt instead.
const departments: Record<string, Visual> = {
  men: { icon: Shirt, tile: 'from-slate-100 to-slate-200 text-slate-800', accent: 'text-slate-800' },
  women: { icon: Sparkles, tile: 'from-brand-50 to-brand-100 text-brand-700', accent: 'text-brand-700' },
  accessories: { icon: Gem, tile: 'from-violet-50 to-violet-100 text-violet-700', accent: 'text-violet-700' },
};

const categories: Record<string, LucideIcon> = {
  watches: Watch,
  sunglasses: Glasses,
  handbags: ShoppingBag,
  backpacks: Backpack,
  wallets: Wallet,
  belts: Tag,
  jewelry: Gem,
  caps: Tag,
  scarves: Scissors,
};

const ACCESSORIES = new Set(Object.keys(categories));

export function categoryVisual(slug: string | undefined): Visual {
  if (!slug) return departments.men;
  if (departments[slug]) return departments[slug];
  if (ACCESSORIES.has(slug)) return { ...departments.accessories, icon: categories[slug] };
  if (slug.startsWith('women-')) return departments.women;
  return departments.men;
}
