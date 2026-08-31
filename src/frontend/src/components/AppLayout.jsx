import { NavLink } from 'react-router-dom';
import { Cpu, LogOut, UserRound } from 'lucide-react';
import { adminNavItems, navItems } from '@/nav-items';
import { useAuth } from '@/auth/AuthContext';
import { Button } from '@/components/ui/button';

export const AppLayout = ({ children }) => {
  const { user: currentUser, signOut } = useAuth();

  return (
    <div className="min-h-screen bg-slate-100 text-slate-950">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-slate-200 bg-white lg:block">
        <div className="flex h-16 items-center gap-3 border-b border-slate-200 px-5">
          <div className="flex h-10 w-10 items-center justify-center rounded-md bg-slate-950 text-white">
            <Cpu className="h-5 w-5" />
          </div>
          <div>
            <div className="text-sm font-semibold">算力预约平台</div>
            <div className="text-xs text-slate-500">用户侧门户</div>
          </div>
        </div>
        <nav className="space-y-1 p-3">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                [
                  'flex items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-slate-950 text-white'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950',
                ].join(' ')
              }
            >
              {item.icon}
              {item.title}
            </NavLink>
          ))}
          {currentUser?.roles?.includes('ADMIN') && <div className="mt-5 border-t border-slate-100 pt-4"><div className="px-3 pb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">管理员</div>{adminNavItems.map((item) => <NavLink key={item.to} to={item.to} className={({ isActive }) => ['flex items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium transition-colors', isActive ? 'bg-slate-950 text-white' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950'].join(' ')}>{item.icon}{item.title}</NavLink>)}</div>}
        </nav>
      </aside>

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 backdrop-blur">
          <div className="flex h-16 items-center justify-between px-4 sm:px-6 lg:px-8">
            <div>
              <div className="text-sm font-semibold lg:hidden">算力预约平台</div>
              <div className="hidden text-sm text-slate-500 lg:block">AI compute resource booking</div>
            </div>
            <div className="flex items-center gap-2">
              <div className="flex items-center gap-2 rounded-md border border-slate-200 px-3 py-1.5 text-sm text-slate-700">
              <UserRound className="h-4 w-4" />
              当前用户：{currentUser?.username || currentUser?.userName}
              {currentUser?.roles?.includes('ADMIN') && <span className="rounded bg-slate-900 px-1.5 py-0.5 text-xs text-white">管理员</span>}
              </div>
              <Button variant="outline" size="sm" onClick={signOut}><LogOut className="mr-1.5 h-4 w-4" />退出</Button>
            </div>
          </div>
          <nav className="flex gap-2 border-t border-slate-100 px-4 py-2 lg:hidden">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  [
                    'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium',
                    isActive ? 'bg-slate-950 text-white' : 'text-slate-600',
                  ].join(' ')
                }
              >
                {item.icon}
                {item.title}
              </NavLink>
            ))}
            {currentUser?.roles?.includes('ADMIN') && adminNavItems.map((item) => <NavLink key={item.to} to={item.to} className={({ isActive }) => ['flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium', isActive ? 'bg-slate-950 text-white' : 'text-slate-600'].join(' ')}>{item.icon}{item.title}</NavLink>)}
          </nav>
        </header>

        <main className="px-4 py-6 sm:px-6 lg:px-8">{children}</main>
      </div>
    </div>
  );
};
