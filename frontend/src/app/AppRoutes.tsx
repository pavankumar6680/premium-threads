import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router';
import { PageSpinner } from '../components/ui/Spinner';
import { AccountPage } from '../features/account/AccountPage';
import { LoginPage } from '../features/auth/LoginPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { RequireAuth } from '../features/auth/RequireAuth';
import { BundleWorkspacePage } from '../features/bundles/BundleWorkspacePage';
import { MyBundlesPage } from '../features/bundles/MyBundlesPage';
import { CartPage } from '../features/cart/CartPage';
import { BrandPage, BrandsPage } from '../features/catalog/BrandsPage';
import { CatalogPage } from '../features/catalog/CatalogPage';
import { ProductPage } from '../features/catalog/ProductPage';
import { CheckoutPage } from '../features/checkout/CheckoutPage';
import { GoalPage } from '../features/goals/GoalPage';
import { GoalsPage } from '../features/goals/GoalsPage';
import { GoalWizardPage } from '../features/goals/wizard/GoalWizardPage';
import { OrderDetailPage } from '../features/orders/OrderDetailPage';
import { OrdersPage } from '../features/orders/OrdersPage';
import { RecommendationsPage } from '../features/recommendations/RecommendationsPage';
import { OutfitBuilderPage } from '../features/outfit/OutfitBuilderPage';
import { WishlistPage } from '../features/wishlist/WishlistPage';
import { HomePage } from '../pages/HomePage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { RootLayout } from './layout/RootLayout';

const AdminApp = lazy(() => import('../features/admin/AdminApp'));

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<RootLayout />}>
        <Route index element={<HomePage />} />
        <Route path="shop" element={<CatalogPage />} />
        <Route path="products/:slug" element={<ProductPage />} />
        <Route path="brands" element={<BrandsPage />} />
        <Route path="brands/:slug" element={<BrandPage />} />
        <Route path="outfit" element={<OutfitBuilderPage />} />
        <Route path="goals" element={<GoalsPage />} />
        <Route path="goals/new" element={<GoalWizardPage />} />
        <Route path="goals/:id" element={<GoalPage />} />
        <Route path="recommendations/:runId" element={<RecommendationsPage />} />
        <Route path="bundles/:id" element={<BundleWorkspacePage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route element={<RequireAuth />}>
          <Route path="account" element={<AccountPage />} />
          <Route path="cart" element={<CartPage />} />
          <Route path="checkout" element={<CheckoutPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/:id" element={<OrderDetailPage />} />
          <Route path="wishlist" element={<WishlistPage />} />
          <Route path="bundles" element={<MyBundlesPage />} />
        </Route>
        <Route element={<RequireAuth role="ADMIN" />}>
          <Route
            path="admin/*"
            element={
              <Suspense fallback={<PageSpinner />}>
                <AdminApp />
              </Suspense>
            }
          />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
