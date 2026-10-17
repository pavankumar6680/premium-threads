import {
  Aperture,
  Armchair,
  BellRing,
  Bike,
  Camera,
  CircuitBoard,
  Cpu,
  Dumbbell,
  Grid3x3,
  Headphones,
  House,
  Image,
  Infinity as InfinityIcon,
  Keyboard,
  LampDesk,
  Laptop,
  Layers,
  Lightbulb,
  MemoryStick,
  Mic,
  Monitor,
  Mouse,
  Package,
  PcCase,
  Plug,
  RectangleHorizontal,
  SlidersVertical,
  Speaker,
  Triangle,
  Webcam,
  Weight,
  BedSingle,
  type LucideIcon,
} from 'lucide-react';

type Visual = { icon: LucideIcon; tile: string; accent: string };

// Product photography is not part of the demo data; each category gets a consistent illustrated tile.
const departments: Record<string, { icon: LucideIcon; tile: string; accent: string }> = {
  'home-office': { icon: Armchair, tile: 'from-amber-50 to-orange-100 text-amber-700', accent: 'bg-amber-100 text-amber-800' },
  computers: { icon: Monitor, tile: 'from-sky-50 to-blue-100 text-blue-700', accent: 'bg-blue-100 text-blue-800' },
  audio: { icon: Headphones, tile: 'from-violet-50 to-purple-100 text-violet-700', accent: 'bg-violet-100 text-violet-800' },
  photography: { icon: Camera, tile: 'from-rose-50 to-pink-100 text-rose-700', accent: 'bg-rose-100 text-rose-800' },
  fitness: { icon: Dumbbell, tile: 'from-emerald-50 to-green-100 text-emerald-700', accent: 'bg-emerald-100 text-emerald-800' },
  'smart-home': { icon: House, tile: 'from-teal-50 to-cyan-100 text-teal-700', accent: 'bg-teal-100 text-teal-800' },
};

const categories: Record<string, [department: string, icon: LucideIcon]> = {
  desks: ['home-office', RectangleHorizontal],
  chairs: ['home-office', Armchair],
  'desk-lamps': ['home-office', LampDesk],
  'monitor-arms': ['home-office', Monitor],
  monitors: ['computers', Monitor],
  keyboards: ['computers', Keyboard],
  mice: ['computers', Mouse],
  webcams: ['computers', Webcam],
  desktops: ['computers', PcCase],
  laptops: ['computers', Laptop],
  cpus: ['computers', Cpu],
  motherboards: ['computers', CircuitBoard],
  headphones: ['audio', Headphones],
  microphones: ['audio', Mic],
  'audio-interfaces': ['audio', SlidersVertical],
  'mic-arms': ['audio', Mic],
  'acoustic-panels': ['audio', Grid3x3],
  cameras: ['photography', Camera],
  lenses: ['photography', Aperture],
  tripods: ['photography', Triangle],
  'studio-lighting': ['photography', Lightbulb],
  backdrops: ['photography', Image],
  'memory-cards': ['photography', MemoryStick],
  dumbbells: ['fitness', Dumbbell],
  kettlebells: ['fitness', Weight],
  benches: ['fitness', BedSingle],
  'exercise-mats': ['fitness', Layers],
  'resistance-bands': ['fitness', InfinityIcon],
  'cardio-machines': ['fitness', Bike],
  'smart-speakers': ['smart-home', Speaker],
  'smart-lighting': ['smart-home', Lightbulb],
  'smart-plugs': ['smart-home', Plug],
  'video-doorbells': ['smart-home', BellRing],
};

const fallback: Visual = { icon: Package, tile: 'from-slate-50 to-slate-100 text-slate-600', accent: 'bg-slate-100 text-slate-700' };

export function categoryVisual(slug: string | undefined): Visual {
  if (!slug) return fallback;
  const department = departments[slug];
  if (department) return department;
  const entry = categories[slug];
  if (!entry) return fallback;
  const [dept, icon] = entry;
  return { ...departments[dept], icon };
}

