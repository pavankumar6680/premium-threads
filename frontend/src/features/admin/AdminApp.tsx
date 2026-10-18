import { Route, Routes } from 'react-router';
import { NotFoundPage } from '../../pages/NotFoundPage';
import { ActivityPage } from './ActivityPage';
import { AdminLayout } from './AdminLayout';
import { AdminOrderPage } from './AdminOrderPage';
import { CategoriesPage } from './CategoriesPage';
import { DashboardPage } from './DashboardPage';
import { OrdersPage } from './OrdersPage';
import { ProductEditorPage } from './ProductEditorPage';
import { ProductsPage } from './ProductsPage';
import { RulesPage } from './RulesPage';
import { TemplatesPage } from './TemplatesPage';

/** The admin area's routes (under /admin). Loaded lazily, so shoppers never download admin code. */
export default function AdminApp() {
  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="products/:id" element={<ProductEditorPage />} />
        <Route path="orders" element={<OrdersPage />} />
        <Route path="orders/:id" element={<AdminOrderPage />} />
        <Route path="categories" element={<CategoriesPage />} />
        <Route path="rules" element={<RulesPage />} />
        <Route path="templates" element={<TemplatesPage />} />
        <Route path="activity" element={<ActivityPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
