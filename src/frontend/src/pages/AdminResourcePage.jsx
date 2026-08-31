import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Cpu, Loader2, Power } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { getErrorPayload } from '@/lib/format';
import { listAdminMachines, updateMachineStatus } from '@/services/reservationApi';
import { useAuth } from '@/auth/AuthContext';

export const AdminResourcePage = () => {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const machinesQuery = useQuery({ queryKey: ['admin', 'machines'], queryFn: async () => (await listAdminMachines()).data });
  const statusMutation = useMutation({
    mutationFn: ({ id, status }) => updateMachineStatus(id, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'machines'] }),
  });

  if (!user?.roles?.includes('ADMIN')) return <div className="rounded-md border border-red-200 bg-red-50 p-5 text-sm text-red-700">没有管理员权限</div>;
  const machines = machinesQuery.data || [];
  return <div className="mx-auto max-w-5xl space-y-6"><section><div className="flex items-center gap-2 text-sm font-medium text-slate-500"><Power className="h-4 w-4" />系统管理</div><h1 className="mt-2 text-2xl font-semibold">资源机器配置</h1><p className="mt-2 text-sm text-slate-500">停用机器不会删除历史预约；停用后不再展示可预约时段，重新启用后会继续生成后续时段。</p></section>{machinesQuery.isLoading ? <div className="flex h-48 items-center justify-center rounded-md border bg-white"><Loader2 className="mr-2 h-5 w-5 animate-spin" />正在加载机器</div> : machinesQuery.isError ? <div className="rounded-md border border-red-200 bg-red-50 p-5 text-sm text-red-700">加载失败：{getErrorPayload(machinesQuery.error).message}</div> : <div className="grid gap-4 md:grid-cols-2">{machines.map((machine) => { const active = machine.status === 'ACTIVE'; return <Card key={machine.id}><CardHeader><div className="flex items-center justify-between gap-3"><CardTitle className="flex items-center gap-2 text-lg"><Cpu className="h-5 w-5" />{machine.machineName}</CardTitle><Badge variant={active ? 'default' : 'secondary'}>{active ? '启用' : '停用'}</Badge></div></CardHeader><CardContent className="flex items-center justify-between"><div className="text-sm text-slate-500">{machine.resourceType} · {machine.gpuModel}</div><Button variant={active ? 'outline' : 'default'} disabled={statusMutation.isPending} onClick={() => statusMutation.mutate({ id: machine.id, status: active ? 'INACTIVE' : 'ACTIVE' })}>{statusMutation.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Power className="mr-2 h-4 w-4" />}{active ? '停用' : '启用'}</Button></CardContent></Card>; })}</div>}</div>;
};
