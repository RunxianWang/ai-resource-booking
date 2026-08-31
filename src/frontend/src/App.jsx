import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { HashRouter, Navigate, Routes, Route, Outlet, useLocation } from "react-router-dom";
import { AppLayout } from "@/components/AppLayout";
import { navItems, adminNavItems } from "./nav-items";
import { AuthProvider, useAuth } from '@/auth/AuthContext';
import { LoginPage } from '@/pages/LoginPage';
import { RegisterPage } from '@/pages/RegisterPage';

const queryClient = new QueryClient();

const RequireAuth = () => {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="flex min-h-screen items-center justify-center bg-slate-100 text-sm text-slate-500">正在检查登录状态…</div>;
  return user ? <Outlet /> : <Navigate to="/login" replace state={{ from: location.pathname }} />;
};

const ProtectedLayout = () => <AppLayout><Outlet /></AppLayout>;

const App = () => <QueryClientProvider client={queryClient}><TooltipProvider><Toaster /><AuthProvider><HashRouter><Routes>
  <Route path="/login" element={<LoginPage />} />
  <Route path="/register" element={<RegisterPage />} />
  <Route element={<RequireAuth />}>
    <Route element={<ProtectedLayout />}>
      <Route path="/" element={<Navigate to="/resources" replace />} />
      {navItems.map(({ to, page }) => <Route key={to} path={to} element={page} />)}
      {adminNavItems.map(({ to, page }) => <Route key={to} path={to} element={page} />)}
    </Route>
  </Route>
</Routes></HashRouter></AuthProvider></TooltipProvider></QueryClientProvider>;

export default App;
