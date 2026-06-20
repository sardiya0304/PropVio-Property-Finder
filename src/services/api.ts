import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL
  ? `${import.meta.env.VITE_API_BASE_URL}/api`
  : 'http://localhost:8080/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT on every request
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('Propvio_token');
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error) => Promise.reject(error)
);

// Auto-logout on 401
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('Propvio_token');
      localStorage.removeItem('Propvio_user');
    }
    return Promise.reject(error);
  }
);

// ═══════════════════════════════════════════════════════════
// API Endpoints — aligned with Spring Boot /api/user/* routes
// ═══════════════════════════════════════════════════════════

export const userAPI = {
  register: (data: { fullName: string; email: string; phone: string; password: string }) =>
    apiClient.post('/user/register', { name: data.fullName, email: data.email, password: data.password }),

  login: (data: { email: string; password: string; rememberMe?: boolean }) =>
    apiClient.post('/user/login', data),

  forgotPassword: (email: string) =>
    apiClient.post('/user/forgot', { email }),

  resetPassword: (token: string, password: string) =>
    apiClient.post(`/user/reset/${token}`, { password }),

  verifyEmail: (token: string) =>
    apiClient.get(`/user/verify/${token}`),

  getProfile: () =>
    apiClient.get('/user/me'),

  logout: () =>
    apiClient.post('/user/logout'),
};

// Properties — Spring Boot: /api/product/*
export const propertiesAPI = {
  getAll: (page = 0, size = 50) =>
    apiClient.get(`/product/list?page=${page}&size=${size}`),

  getById: (id: string) =>
    apiClient.get(`/product/single?id=${id}`),

  filter: (filters: Record<string, unknown>) =>
    apiClient.post('/product/filter', filters),
};

// User-submitted listings — Spring Boot: /api/user/properties
export const userListingsAPI = {
  create: (formData: FormData) =>
    apiClient.post('/user/properties', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),

  getMyListings: () =>
    apiClient.get('/user/properties'),

  update: (id: string, formData: FormData) =>
    apiClient.put(`/user/properties/${id}`, formData, { headers: { 'Content-Type': 'multipart/form-data' } }),

  delete: (id: string) =>
    apiClient.delete(`/user/properties/${id}`),
};

// Appointments — Spring Boot: /api/appointments
export const appointmentsAPI = {
  schedule: (data: {
    propertyId: string;
    date: string;
    time: string;
    name: string;
    email: string;
    phone: string;
    message?: string;
  }) => apiClient.post('/appointments/schedule', data),

  getByUser: () =>
    apiClient.get('/appointments/user'),

  cancel: (id: string, reason?: string) =>
    apiClient.put(`/appointments/cancel/${id}`, { cancelReason: reason }),
};

// AI Hub (firecrawl + GitHub Models search)
export const aiAPI = {
  search: (data: Record<string, unknown>) => {
    const githubKey    = localStorage.getItem('Propvio_github_key');
    const firecrawlKey = localStorage.getItem('Propvio_firecrawl_key');
    return apiClient.post('/ai/search', data, {
      headers: {
        ...(githubKey    && { 'X-Github-Key': githubKey }),
        ...(firecrawlKey && { 'X-Firecrawl-Key': firecrawlKey }),
      },
    });
  },

  locationTrends: (city: string) => {
    const githubKey    = localStorage.getItem('Propvio_github_key');
    const firecrawlKey = localStorage.getItem('Propvio_firecrawl_key');
    return apiClient.get(`/locations/${encodeURIComponent(city)}/trends`, {
      headers: {
        ...(githubKey    && { 'X-Github-Key': githubKey }),
        ...(firecrawlKey && { 'X-Firecrawl-Key': firecrawlKey }),
      },
    });
  },

  validateKeys: (keys?: { githubKey?: string; firecrawlKey?: string }) => {
    const githubKey    = (keys?.githubKey    ?? localStorage.getItem('Propvio_github_key')    ?? '').trim();
    const firecrawlKey = (keys?.firecrawlKey ?? localStorage.getItem('Propvio_firecrawl_key') ?? '').trim();
    return apiClient.post('/ai/validate-keys', {}, {
      headers: {
        ...(githubKey    && { 'X-Github-Key': githubKey }),
        ...(firecrawlKey && { 'X-Firecrawl-Key': firecrawlKey }),
      },
    });
  },
};

export const apiKeyStorage = {
  getGithubKey:    ()          => localStorage.getItem('Propvio_github_key')    || '',
  getFirecrawlKey: ()          => localStorage.getItem('Propvio_firecrawl_key') || '',
  setGithubKey:    (key: string) => localStorage.setItem('Propvio_github_key', key),
  setFirecrawlKey: (key: string) => localStorage.setItem('Propvio_firecrawl_key', key),
  hasKeys: () => !!(localStorage.getItem('Propvio_github_key') && localStorage.getItem('Propvio_firecrawl_key')),
  clear: () => {
    localStorage.removeItem('Propvio_github_key');
    localStorage.removeItem('Propvio_firecrawl_key');
  },
};

// AI Price Calculator — Spring Boot: POST /api/ai/calculate-price
export const aiCalculatorAPI = {
  calculate: (data: {
    city: string;
    bhk: number;
    areaSqft: number;
    ageYears: number;
    furnishing: string;
  }) => apiClient.post('/ai/calculate-price', data),

  supportedCities: () => apiClient.get('/ai/supported-cities'),
};

// Contact form — Spring Boot: POST /api/forms/submit
export const contactAPI = {
  submit: (data: { name: string; email: string; phone: string; message: string }) =>
    apiClient.post('/forms/submit', data),
};

export default apiClient;
