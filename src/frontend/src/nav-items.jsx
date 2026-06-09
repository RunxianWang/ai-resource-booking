import { CalendarClock, Server } from 'lucide-react';
import { MyBookingsPage } from './pages/MyBookingsPage.jsx';
import { ResourceListPage } from './pages/ResourceListPage.jsx';

export const navItems = [
  {
    title: '资源列表',
    to: '/resources',
    icon: <Server className="h-4 w-4" />,
    page: <ResourceListPage />,
  },
  {
    title: '我的预约',
    to: '/bookings',
    icon: <CalendarClock className="h-4 w-4" />,
    page: <MyBookingsPage />,
  },
];
