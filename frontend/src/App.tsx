import { Navigate, Route, Routes } from 'react-router-dom'
import AuthPage from './AuthPage'
import VerifyPage from './VerifyPage'
import PasswordResetPage from './PasswordResetPage'
import ProfilePage from './ProfilePage'
import AdminPage from './AdminPage'
import WarehousePage from './WarehousePage'
import DeliveryPage from './DeliveryPage'
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
import './App.css'
import './mockup-theme.css'

export default function App() {
  return <><SessionIdleNotice /><Routes>
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
    <Route path="/me/orders/:id" element={<OrderDetailPage />} />
    <Route path="/checkout" element={<CheckoutPage />} />
    <Route path="/email-change/confirm" element={<EmailChangeConfirmPage />} />
    <Route path="/admin/*" element={<AdminPage />} />
    <Route path="/manager/*" element={<AdminPage />} />
    <Route path="/support/*" element={<AdminPage />} />
    <Route path="/warehouse/*" element={<InternalRoleRoute role="WAREHOUSE"><WarehousePage /></InternalRoleRoute>} />
    <Route path="/delivery/*" element={<InternalRoleRoute role="DELIVERY"><DeliveryPage /></InternalRoleRoute>} />
    <Route path="/internal/*" element={<InternalEntryRedirect />} />
    <Route path="/staff" element={<Navigate to="/internal" replace />} />
  </Routes></>
}
