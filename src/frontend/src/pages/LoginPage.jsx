import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Cpu, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/auth/AuthContext';

export const LoginPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { signIn } = useAuth();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin123');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault(); setError(''); setSubmitting(true);
    try { await signIn(username, password); navigate(location.state?.from || '/resources', { replace: true }); }
    catch (err) { setError(err.response?.data?.reason || err.response?.data?.message || '登录失败'); }
    finally { setSubmitting(false); }
  };

  return <AuthShell title="登录账号" subtitle="登录后预约 AI 算力资源">
    <form className="space-y-5" onSubmit={submit}>
      <Field label="用户名"><Input value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus /></Field>
      <Field label="密码"><Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required /></Field>
      {error && <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
      <Button className="w-full" disabled={submitting}>{submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}登录</Button>
      <p className="text-center text-sm text-slate-500">还没有账号？ <Link className="font-medium text-slate-950 underline" to="/register">注册新用户</Link></p>
    </form>
  </AuthShell>;
};

const Field = ({ label, children }) => <div className="space-y-2"><Label>{label}</Label>{children}</div>;
const AuthShell = ({ title, subtitle, children }) => <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-8"><div className="w-full max-w-md rounded-xl border border-slate-200 bg-white p-8 shadow-sm"><div className="mb-8 text-center"><div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-slate-950 text-white"><Cpu className="h-6 w-6" /></div><h1 className="text-2xl font-bold text-slate-950">{title}</h1><p className="mt-2 text-sm text-slate-500">{subtitle}</p></div>{children}</div></div>;
