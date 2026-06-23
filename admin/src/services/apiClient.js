import axios from 'axios';
import { APP_CONSTANTS, backendurl } from '../config/constants';

const apiClient = axios.create({
  baseURL: backendurl,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(APP_CONSTANTS.TOKEN_KEY);
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    // For FormData (file uploads), remove Content-Type so the browser sets it
    // automatically with the correct multipart boundary string.
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(APP_CONSTANTS.TOKEN_KEY);
      localStorage.removeItem(APP_CONSTANTS.IS_ADMIN_KEY);
    }
    return Promise.reject(error);
  }
);

export default apiClient;
