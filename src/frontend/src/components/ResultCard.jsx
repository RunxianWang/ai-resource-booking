import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { AlertCircle, CheckCircle2, TerminalSquare } from 'lucide-react';

export const ResultCard = ({ data, mode = 'debug' }) => {
  if (!data) return null;

  if (mode === 'business') {
    const Icon = data.isSuccess ? CheckCircle2 : AlertCircle;
    return (
      <Card className={data.isSuccess ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'}>
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-lg">
            <Icon className={data.isSuccess ? 'h-5 w-5 text-emerald-700' : 'h-5 w-5 text-amber-700'} />
            {data.title}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <p>{data.description}</p>
          <div className="flex flex-wrap gap-2">
            <Badge variant={data.isSuccess ? 'default' : 'destructive'}>{data.code}</Badge>
            {data.bookingId && <Badge variant="outline">bookingId: {data.bookingId}</Badge>}
            {data.traceId && <Badge variant="outline">traceId: {data.traceId}</Badge>}
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-lg">
          <TerminalSquare className="h-5 w-5" />
          调试输出
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          {data.action && <Badge variant="outline">{data.action}</Badge>}
          {data.code && <Badge variant={data.code === 'SUCCESS' ? 'default' : 'destructive'}>{data.code}</Badge>}
          {data.message && <span className="text-muted-foreground">{data.message}</span>}
        </div>
        <pre className="max-h-80 overflow-auto rounded-md bg-slate-950 p-3 text-xs text-slate-100">
          {JSON.stringify(data, null, 2)}
        </pre>
      </CardContent>
    </Card>
  );
};
