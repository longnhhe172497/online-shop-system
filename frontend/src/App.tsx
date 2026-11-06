import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import AuthPage from './AuthPage'
import VerifyPage from './VerifyPage'
import PasswordResetPage from './PasswordResetPage'
import ProfilePage from './ProfilePage'
import InternalEntryRedirect from './InternalEntryRedirect'
import InternalRoleRoute from './InternalRoleRoute'
import { AcceptInvitationPage, MfaLoginPage, RegistrationSentPage,
  ResendVerificationPage } from './AuthExtraPages'
import AccountSecurityPage from './AccountSecurityPage'
import AccountSessionsPage from './AccountSessionsPage'
import { EmailChangePage, EmailChangeConfirmPage } from './EmailChangePages'
import SessionIdleNotice from './SessionIdleNotice'
import CheckoutPage from './CheckoutPage'
import OrdersPage from './OrdersPage'
import OrderDetailPage from './OrderDetailPage'
import StorefrontPage from './StorefrontPage'
import ProductDetailPage from './ProductDetailPage'
import CartPage from './CartPage'
import NotFoundPage from './NotFoundPage'
import './storeFonts'
import './design-tokens.css'
import './App.css'
import './mockup-theme.css'
import './account-theme.css'
import './internal-theme.css'
import './ui-polish.css'

const AdminPage = lazy(() => import('./AdminPage'))
const WarehousePage = lazy(() => import('./WarehousePage'))
const ManagerAfterSalesPage = lazy(() => import('./ManagerAfterSalesPage'))
const CustomerSupportPage = lazy(() => import('./CustomerSupportPage'))
const SupportTicketsPage = lazy(() => import('./SupportTicketsPage'))
const DeliveryPage = lazy(() => import('./DeliveryPage'))

function RouteLoading() {
  return <main className="route-loading" role="status" aria-live="polite">
    <span className="route-loading-mark" aria-hidden="true">F</span>
    <p>Đang chuẩn bị không gian làm việc…</p>
  </main>
}

export default function App() {
  return <><SessionIdleNotice /><Suspense fallback={<RouteLoading />}><Routes>
    <Route path="/" element={<StorefrontPage />} />
    <Route path="/products/:id" element={<ProductDetailPage />} />
    <Route path="/cart" element={<CartPage />} />
    <Route path="/auth" element={<AuthPage />} />
    <Route path="/register" element={<AuthPage />} />
    <Route path="/registration-sent" element={<RegistrationSentPage />} />
    <Route path="/resend-verification" element={<ResendVerificationPage />} />
    <Route path="/auth/mfa" element={<MfaLoginPage />} />
    <Route path="/accept-invitation" element={<AcceptInvitationPage />} />
    <Route path="/verify-email" element={<VerifyPage />} />
    <Route path="/forgot-password" element={<PasswordResetPage />} />
    <Route path="/reset-password" element={<PasswordResetPage />} />
    <Route path="/me" element={<ProfilePage />} />
    <Route path="/me/addresses" element={<ProfilePage />} />
    <Route path="/me/security" element={<AccountSecurityPage />} />
    <Route path="/me/sessions" element={<AccountSessionsPage />} />
    <Route path="/me/email" element={<EmailChangePage />} />
    <Route path="/me/orders" element={<OrdersPage />} />
    <Route path="/me/support" element={<CustomerSupportPage />} />
    <Route path="/me/orders/:id" element={<OrderDetailPage />} />
    <Route path="/checkout" element={<CheckoutPage />} />
    <Route path="/email-change/confirm" element={<EmailChangeConfirmPage />} />
    <Route path="/admin/*" element={<AdminPage />} />
    <Route path="/manager/after-sales" element={<InternalRoleRoute role="MANAGER"><ManagerAfterSalesPage /></InternalRoleRoute>} />
    <Route path="/manager/*" element={<AdminPage />} />
    <Route path="/support/tickets" element={<InternalRoleRoute role="SUPPORT"><SupportTicketsPage /></InternalRoleRoute>} />
    <Route path="/support/*" element={<AdminPage />} />
    <Route path="/warehouse/*" element={<InternalRoleRoute role="WAREHOUSE"><WarehousePage /></InternalRoleRoute>} />
    <Route path="/delivery/*" element={<InternalRoleRoute role="DELIVERY"><DeliveryPage /></InternalRoleRoute>} />
    <Route path="/internal/*" element={<InternalEntryRedirect />} />
    <Route path="/staff" element={<Navigate to="/internal" replace />} />
    <Route path="*" element={<NotFoundPage />} />
  </Routes></Suspense></>
}
