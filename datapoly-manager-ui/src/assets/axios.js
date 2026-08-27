import Axios from 'axios';

var root = process.env.API_ROOT || '';
const axios = Axios.create();

// Request interceptor
axios.interceptors.request.use((config) => {
  // Ensure config exists
  if (!config) {
    console.error('Axios request blocked: config is undefined');
    return Promise.reject(new Error('Config is undefined'));
  }
  // Ensure URL exists
  if (!config.url) {
    console.error('Axios request blocked: missing URL', config);
    return Promise.reject(new Error('Missing URL'));
  }
  // Prepend root to relative URLs
  if (root && config.url && typeof config.url === 'string') {
    config.url = root + config.url;
  }
  // Send language header for backend i18n
  const locale = localStorage.getItem('locale') || 'zh-CN';
  config.headers['Accept-Language'] = locale;
  return config;
});

export default axios;
