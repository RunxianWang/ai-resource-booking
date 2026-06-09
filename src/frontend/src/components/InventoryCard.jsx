import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export const InventoryCard = ({ data }) => {
  if (!data) return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle>库存信息</CardTitle>
      </CardHeader>
      <CardContent className="grid grid-cols-3 gap-4">
        <div className="text-center">
          <div className="text-sm text-muted-foreground">总库存</div>
          <div className="text-2xl font-bold">{data.totalCount ?? '-'}</div>
        </div>
        <div className="text-center">
          <div className="text-sm text-muted-foreground">MySQL 剩余</div>
          <div className="text-2xl font-bold">{data.mysqlAvailableCount ?? '-'}</div>
        </div>
        <div className="text-center">
          <div className="text-sm text-muted-foreground">Redis 剩余</div>
          <div className="text-2xl font-bold">{data.redisAvailableCount ?? '-'}</div>
        </div>
      </CardContent>
    </Card>
  );
};
