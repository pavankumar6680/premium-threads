import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { Page } from '../catalog/types';
import type { Order, OrderStatus } from '../orders/api';

// ------------------------------------------------------------------ types

export type AttributeType = 'TEXT' | 'NUMBER' | 'BOOL' | 'LIST';

export type AdminProductRow = {
  id: number;
  sku: string;
  slug: string;
  name: string;
  brand: string;
  price: number;
  active: boolean;
  qualityTier: number;
  ratingAvg: number;
  ratingCount: number;
  categorySlug: string;
  categoryName: string;
  /** total over active variants */
  stock: number;
  variantCount: number;
  compareAtPrice: number | null;
  gender: 'MEN' | 'WOMEN' | 'UNISEX';
  featured: boolean;
  updatedAt: string;
};

export type AdminProduct = {
  id: number;
  sku: string;
  slug: string;
  name: string;
  brand: string;
  description: string;
  price: number;
  qualityTier: number;
  active: boolean;
  categorySlug: string;
  stock: number;
  tags: string[];
  attributes: Record<string, string[]>;
  ratingAvg: number;
  ratingCount: number;
  version: number;
  updatedAt: string;
};

export type ProductInput = {
  categorySlug: string;
  sku: string;
  name: string;
  brand: string;
  description: string;
  price: number;
  qualityTier: number;
  active: boolean;
  tags: string[];
  attributes: Record<string, string[]>;
  version?: number;
  stock?: number;
};

export type AttributeDefinition = { key: string; label: string; type: AttributeType; unit: string | null; filterable: boolean };

export type AdminCategory = {
  id: number;
  slug: string;
  name: string;
  description: string | null;
  departmentSlug: string | null;
  departmentName: string | null;
  leaf: boolean;
  productCount: number;
  attributes: AttributeDefinition[];
};

export type Customer = { id: number; email: string; fullName: string };

export type AdminOrderSummary = {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  createdAt: string;
  total: number;
  currency: string;
  itemCount: number;
  customer: Customer | null;
};

export type AdminOrder = { order: Order; customer: Customer | null; allowedTransitions: OrderStatus[] };

export type Operator =
  | 'EQUALS'
  | 'LIST_CONTAINS'
  | 'LIST_INTERSECTS'
  | 'LTE'
  | 'LTE_SPACE_WIDTH'
  | 'LTE_SPACE_DEPTH'
  | 'REQUIRES_CATEGORY_UNLESS';

export type Severity = 'BLOCK' | 'WARN';

export type RuleInput = {
  code: string;
  sourceCategory: string;
  targetCategory: string | null;
  operator: Operator;
  sourceAttr: string;
  targetAttr: string | null;
  value: string | null;
  severity: Severity;
  label: string;
  message: string;
  active: boolean;
};

export type AdminRule = RuleInput & { id: number };

export type RuleImpact = { evaluated: number; violating: number; notApplicable: number; examples: string[]; summary: string };

export type TemplateItem = {
  categorySlug: string;
  categoryName?: string | null;
  required: boolean;
  selectedByDefault: boolean;
  quantity: number;
  note: string | null;
};

export type TemplateInput = {
  slug: string;
  title: string;
  tagline: string;
  description: string;
  defaultBudget: number;
  contextTag: string | null;
  active: boolean;
  items: TemplateItem[];
  usageOptions: { tag: string; label: string }[];
  keywords: string[];
};

export type AdminTemplate = TemplateInput & { id: number };

export type AnalyticsSummary = {
  days: number;
  from: string;
  sales: {
    revenue: number;
    previousRevenue: number;
    orders: number;
    cancelledOrders: number;
    averageOrderValue: number;
    unitsSold: number;
  };
  daily: { date: string; revenue: number; orders: number }[];
  ordersByStatus: Record<OrderStatus, number>;
  topProducts: { productId: number; name: string; sku: string; units: number; revenue: number }[];
  inventory: {
    activeProducts: number;
    lowStock: number;
    outOfStock: number;
    lowest: { productId: number; name: string; sku: string; stock: number }[];
  };
  goals: { created: number; describedInOwnWords: number; averageBudget: number; byTemplate: { title: string; goals: number }[] };
  recommendations: {
    runs: number;
    infeasibleRuns: number;
    infeasibleShare: number;
    averageDurationMs: number;
    p95DurationMs: number;
    averageNodes: number;
    aiOverviews: number;
    savedBundles: number;
    customizedBundles: number;
  };
  customers: { total: number; newInPeriod: number };
};

export type AuditEntry = {
  id: number;
  actorEmail: string;
  action: string;
  entityType: string;
  entityId: string;
  details: string;
  createdAt: string;
};

// ------------------------------------------------------------------ queries

export type ProductFilters = {
  q: string;
  category: string;
  status: '' | 'active' | 'inactive';
  lowStock: boolean;
  sort: 'name' | 'price' | 'updated' | 'stock';
  page: number;
};

export const adminKeys = {
  all: ['admin'] as const,
  products: (f: ProductFilters) => ['admin', 'products', f] as const,
  product: (id: number) => ['admin', 'product', id] as const,
  categories: ['admin', 'categories'] as const,
  orders: (status: string, orderNumber: string, page: number) => ['admin', 'orders', status, orderNumber, page] as const,
  order: (id: number) => ['admin', 'order', id] as const,
  rules: ['admin', 'rules'] as const,
  templates: ['admin', 'templates'] as const,
  summary: (days: number) => ['admin', 'summary', days] as const,
  audit: (entityType: string, page: number) => ['admin', 'audit', entityType, page] as const,
};

function query(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '' && value !== false) search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : '';
}

export function useAdminProducts(f: ProductFilters) {
  return useQuery({
    queryKey: adminKeys.products(f),
    queryFn: () =>
      api.get<Page<AdminProductRow>>(
        `/admin/products${query({
          q: f.q.trim(),
          category: f.category,
          active: f.status === '' ? undefined : f.status === 'active',
          lowStock: f.lowStock,
          sort: f.sort,
          page: f.page,
        })}`,
      ),
    placeholderData: keepPreviousData,
  });
}

export function useAdminProduct(id: number | null) {
  return useQuery({
    queryKey: adminKeys.product(id ?? 0),
    queryFn: () => api.get<AdminProduct>(`/admin/products/${id}`),
    enabled: id !== null,
  });
}

export function useAdminCategories() {
  return useQuery({ queryKey: adminKeys.categories, queryFn: () => api.get<AdminCategory[]>('/admin/categories') });
}

export function useAdminOrders(status: string, orderNumber: string, page: number) {
  return useQuery({
    queryKey: adminKeys.orders(status, orderNumber, page),
    queryFn: () => api.get<Page<AdminOrderSummary>>(`/admin/orders${query({ status, orderNumber: orderNumber.trim(), page })}`),
    placeholderData: keepPreviousData,
  });
}

export function useAdminOrder(id: number) {
  return useQuery({ queryKey: adminKeys.order(id), queryFn: () => api.get<AdminOrder>(`/admin/orders/${id}`) });
}

export function useRules() {
  return useQuery({ queryKey: adminKeys.rules, queryFn: () => api.get<AdminRule[]>('/admin/compatibility-rules') });
}

export function useTemplates() {
  return useQuery({ queryKey: adminKeys.templates, queryFn: () => api.get<AdminTemplate[]>('/admin/goal-templates') });
}

export function useAnalytics(days: number) {
  return useQuery({
    queryKey: adminKeys.summary(days),
    queryFn: () => api.get<AnalyticsSummary>(`/admin/analytics/summary?days=${days}`),
    placeholderData: keepPreviousData,
  });
}

export function useAuditLog(entityType: string, page: number) {
  return useQuery({
    queryKey: adminKeys.audit(entityType, page),
    queryFn: () => api.get<Page<AuditEntry>>(`/admin/audit-log${query({ entityType, page })}`),
    placeholderData: keepPreviousData,
  });
}

// ------------------------------------------------------------------ mutations

/** Admin changes can affect any admin list (and the storefront), so refresh every admin query. */
function useAdminMutation<TVariables, TResult>(fn: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
      void queryClient.invalidateQueries({ queryKey: ['catalog'] });
      void queryClient.invalidateQueries({ queryKey: ['goals'] });
    },
  });
}

export const useSaveProduct = () =>
  useAdminMutation(({ id, input }: { id: number | null; input: ProductInput }) =>
    id === null ? api.post<AdminProduct>('/admin/products', input) : api.put<AdminProduct>(`/admin/products/${id}`, input),
  );

export const useSetStock = () =>
  useAdminMutation(({ id, quantity }: { id: number; quantity: number }) =>
    api.put<AdminProduct>(`/admin/products/${id}/stock`, { quantity }),
  );

export const useSetProductActive = () =>
  useAdminMutation(({ id, active }: { id: number; active: boolean }) =>
    api.patch<AdminProduct>(`/admin/products/${id}/status`, { active }),
  );

export const useChangeOrderStatus = () =>
  useAdminMutation(({ id, status }: { id: number; status: OrderStatus }) =>
    api.post<AdminOrder>(`/admin/orders/${id}/status`, { status }),
  );

export const useSaveRule = () =>
  useAdminMutation(({ id, input }: { id: number | null; input: RuleInput }) =>
    id === null ? api.post<AdminRule>('/admin/compatibility-rules', input) : api.put<AdminRule>(`/admin/compatibility-rules/${id}`, input),
  );

export const useSetRuleActive = () =>
  useAdminMutation(({ id, active }: { id: number; active: boolean }) =>
    api.patch<AdminRule>(`/admin/compatibility-rules/${id}/active`, { active }),
  );

export const usePreviewRule = () =>
  useMutation({ mutationFn: (input: RuleInput) => api.post<RuleImpact>('/admin/compatibility-rules/preview', input) });

export const useSaveTemplate = () =>
  useAdminMutation(({ id, input }: { id: number | null; input: TemplateInput }) =>
    id === null ? api.post<AdminTemplate>('/admin/goal-templates', input) : api.put<AdminTemplate>(`/admin/goal-templates/${id}`, input),
  );

export const useCreateCategory = () =>
  useAdminMutation((input: { departmentSlug: string; slug: string; name: string; description: string }) =>
    api.post<AdminCategory>('/admin/categories', input),
  );

export const useAddAttribute = () =>
  useAdminMutation(({ category, ...input }: { category: string; key: string; label: string; type: AttributeType; unit: string; filterable: boolean }) =>
    api.post<AdminCategory>(`/admin/categories/${category}/attributes`, input),
  );
