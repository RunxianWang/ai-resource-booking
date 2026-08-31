import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { register } from '@/services/reservationApi';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

export const RegisterPage = () => {
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', password: '', confirm: '' });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const update = (key) => (event) => setForm({ ...form, [key]: event.target.value });
  const submit = async (event) => {
    event.preventDefault(); setError('');
    if (form.password.length < 6) { setError('密码至少需要 6 位'); return; }
    if (form.password !== form.confirm) { setError('两次输入的密码不一致'); return; }
    try { await register(form.username, form.password); setSuccess(true); setTimeout(() => navigate('/login'), 700); }
    catch (err) { setError(err.response?.data?.reason || err.response?.data?.message || '注册失败'); }
  };
  return <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-8"><div className="w-full max-w-md rounded-xl border border-slate-200 bg-white p-8 shadow-sm"><h1 className="text-2xl font-bold text-slate-950">注册新用户</h1><p className="mt-2 text-sm text-slate-500">注册成功后即可预约资源。</p><form className="mt-8 space-y-5" onSubmit={submit}><div className="space-y-2"><Label>用户名</Label><Input value={form.username} onChange={update('username')} required autoFocus /></div><div className="space-y-2"><Label>密码</Label><Input type="password" value={form.password} onChange={update('password')} required /></div><div className="space-y-2"><Label>确认密码</Label><Input type="password" value={form.confirm} onChange={update('confirm')} required /></div>{error && <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}{success && <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">注册成功，正在返回登录页…</div>}<Button className="w-full" disabled={success}>注册</Button><p className="text-center text-sm text-slate-500">已有账号？ <Link className="font-medium text-slate-950 underline" to="/login">返回登录</Link></p></form></div></div>;
};
