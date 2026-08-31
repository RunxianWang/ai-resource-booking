import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { getCurrentUser, login, logout } from '@/services/reservationApi';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = async () => {
    try {
      const response = await getCurrentUser();
      setUser(response.data);
      return response.data;
    } catch {
      setUser(null);
      return null;
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { refreshUser(); }, []);

  const value = useMemo(() => ({
    user,
    loading,
    async signIn(username, password) {
      const response = await login(username, password);
      setUser(response.data);
      return response.data;
    },
    async signOut() {
      await logout();
      setUser(null);
    },
  }), [user, loading]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => useContext(AuthContext);
