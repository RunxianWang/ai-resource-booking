import axios from 'axios';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
});

api.interceptors.request.use((config) => {
  config.headers['X-Trace-Id'] = `frontend-${Date.now()}`;
  if (config.method === 'post' || config.method === 'put' || config.method === 'patch') {
    config.headers['Content-Type'] = 'application/json';
  }
  return config;
});
