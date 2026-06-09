import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { CheckCircle, XCircle } from 'lucide-react';

export const ConsistencyCard = ({ data }) => {
  if (!data) return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle>一致性校验结果</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex items-center justify-between">
          <span>库存一致性</span>
          {data.stockConsistent ? (
            <div className="flex items-center text-green-600 font-medium">
              <CheckCircle className="w-5 h-5 mr-1" />
              <span>true</span>
            </div>
          ) : (
            <div className="flex items-center text-red-600 font-medium">
              <XCircle className="w-5 h-5 mr-1" />
              <span>false</span>
            </div>
          )}
        </div>
        <div className="flex items-center justify-between">
          <span>消息一致性</span>
          {data.messageConsistent ? (
            <div className="flex items-center text-green-600 font-medium">
              <CheckCircle className="w-5 h-5 mr-1" />
              <span>true</span>
            </div>
          ) : (
            <div className="flex items-center text-red-600 font-medium">
              <XCircle className="w-5 h-5 mr-1" />
              <span>false</span>
            </div>
          )}
        </div>
        <div className="grid grid-cols-2 gap-3 pt-2 text-sm text-muted-foreground">
          <span>成功预约：{data.successBookingCount ?? '-'}</span>
          <span>消息日志：{data.messageLogCount ?? '-'}</span>
          <span>已消费消息：{data.consumedMessageCount ?? '-'}</span>
          <span>消费日志：{data.consumeLogCount ?? '-'}</span>
        </div>
      </CardContent>
    </Card>
  );
};
