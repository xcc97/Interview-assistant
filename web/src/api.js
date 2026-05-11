import { useSessionStore } from './stores/session';

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
const clientSecret = import.meta.env.VITE_CLIENT_SECRET || '';
const enableMockPayment = import.meta.env.VITE_ENABLE_MOCK_PAYMENT === 'true';

async function request(path, options = {}) {
  const session = useSessionStore();
  const headers = {
    'Content-Type': 'application/json',
    ...(options.useClientSecret && clientSecret ? { 'X-Client-Secret': clientSecret } : {}),
    ...(session.token ? { Authorization: `Bearer ${session.token}` } : {}),
    ...(options.headers || {}),
  };

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let message = '请求失败';
    try {
      const errorData = await response.json();
      message = errorData.message || errorData.error || message;
    } catch {
      message = `${message}（HTTP ${response.status}）`;
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

export async function login(payload) {
  return request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function register(payload) {
  return request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function getCurrentUser() {
  return request('/api/auth/me', {
    method: 'GET',
  });
}

export async function getProfile() {
  return request('/api/user/profile', {
    method: 'GET',
  });
}

export async function getPlans() {
  return request('/api/plans', {
    method: 'GET',
  });
}

export async function createOrder(payload) {
  return request('/api/orders', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function getOrders() {
  return request('/api/orders', {
    method: 'GET',
  });
}

export async function createPayment(payload) {
  return request('/api/payment/create', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function mockPaymentPaid(payload) {
  return request('/api/payment/mock-paid', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function startUsageSession(payload) {
  return request('/api/usage/start', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function finishUsageSession(payload) {
  return request('/api/usage/finish', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function getUsageSessions() {
  return request('/api/usage', {
    method: 'GET',
  });
}

export async function getInterviewSessions() {
  return request('/api/interview/sessions', {
    method: 'GET',
  });
}

export async function getInterviewSessionRecords(sessionId) {
  return request(`/api/interview/sessions/${encodeURIComponent(sessionId)}/records`, {
    method: 'GET',
  });
}

export async function getBalanceTransactions() {
  return request('/api/balance/transactions', {
    method: 'GET',
  });
}

export async function getReadiness() {
  return request('/api/admin/readiness', {
    method: 'GET',
  });
}

export async function getAdminOrders(status = '') {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return request(`/api/admin/orders${query}`, {
    method: 'GET',
  });
}

export async function adminCloseOrder(orderId, payload) {
  return request(`/api/admin/orders/${encodeURIComponent(orderId)}/close`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function adminGrantPaidOrder(orderId, payload) {
  return request(`/api/admin/orders/${encodeURIComponent(orderId)}/grant-paid`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function getPaymentCallbackLogs(filters = {}) {
  const params = new URLSearchParams();
  if (filters.orderId) params.set('orderId', filters.orderId);
  if (filters.status) params.set('status', filters.status);
  const query = params.toString() ? `?${params.toString()}` : '';
  return request(`/api/admin/payment-callback-logs${query}`, {
    method: 'GET',
  });
}

export async function getHealth() {
  return request('/api/client/health', {
    method: 'GET',
  });
}

export async function createAsrToken() {
  return request('/api/client/asr/token', {
    method: 'POST',
    useClientSecret: true,
  });
}

export async function analyzeInterview(payload) {
  return request('/api/client/interview/analyze', {
    method: 'POST',
    body: JSON.stringify(payload),
    useClientSecret: true,
  });
}

export function getRuntimeConfig() {
  return {
    apiBaseUrl,
    hasClientSecret: Boolean(clientSecret),
    enableMockPayment,
  };
}
