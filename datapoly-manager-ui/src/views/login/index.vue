```vue
<template>
  <div class="login-container">
    <div class="login-form">
      <div class="logo-section">
        <h1 class="system-title">{{ $t('login.title') }}</h1>
        <p class="system-subtitle">{{ $t('system.welcome') }} DataPoly</p>
      </div>

      <form @submit.prevent="handleLogin"
            class="form-content">
        <div class="input-group">
          <label for="username"
                 class="input-label">{{ $t('login.username') }}</label>
          <input type="text"
                 id="username"
                 v-model="loginForm.username"
                 :placeholder="$t('login.usernamePlaceholder')"
                 class="input-field"
                 :class="{ 'input-error': errors.username }">
          <span class="error-message"
                v-if="errors.username">{{ errors.username }}</span>
        </div>

        <div class="input-group">
          <label for="password"
                 class="input-label">{{ $t('login.password') }}</label>
          <input type="password"
                 id="password"
                 v-model="loginForm.password"
                 :placeholder="$t('login.passwordPlaceholder')"
                 class="input-field"
                 :class="{ 'input-error': errors.password }">
          <span class="error-message"
                v-if="errors.password">{{ errors.password }}</span>
        </div>

        <button type="submit"
                class="login-button"
                :disabled="loading">
          <span v-if="!loading">{{ $t('login.loginBtn') }}</span>
          <span v-if="loading"
                class="loading-text">{{ $t('login.loggingIn') }}</span>
        </button>
      </form>

      <div class="footer-links">
        <p class="copyright">© {{ currentYear }} The DataPoly Authors</p>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'LoginPage',
  data () {
    return {
      loginForm: {
        username: '',
        password: ''
      },
      errors: {},
      loading: false
    }
  },
  computed: {
    currentYear() {
      return new Date().getFullYear();
    }
  },
  created() {
    this.initLocale();
  },
  methods: {
    initLocale() {
      const savedLocale = localStorage.getItem('locale');
      if (savedLocale) {
        this.$i18n.locale = savedLocale;
      } else {
        const browserLang = navigator.language || 'zh-CN';
        const defaultLocale = browserLang.startsWith('en') ? 'en-US' : 'zh-CN';
        this.$i18n.locale = defaultLocale;
        localStorage.setItem('locale', defaultLocale);
      }
    },
    validateForm () {
      this.errors = {}

      if (!this.loginForm.username.trim()) {
        this.errors.username = this.$t('login.usernameRequired')
      }

      if (!this.loginForm.password) {
        this.errors.password = this.$t('login.passwordRequired')
      }

      return Object.keys(this.errors).length === 0
    },
    resetLoading () {
      this.loading = false;
    },
    showMessageBox (msg) {
      this.$alert(msg, this.$t('common.error'), {
        type: 'error',
        confirmButtonText: this.$t('common.confirm')
      });
    },
    handleLogin () {
      if (!this.validateForm()) {
        return
      }
      this.loading = true
      this.$http({
        method: 'POST',
        url: '/user/login',
        data: {
          username: this.loginForm.username,
          password: this.loginForm.password
        },
      }).then(res => {
        if (0 === res.data.code) {
          window.sessionStorage.setItem('token', res.data.data.accessToken);
          window.sessionStorage.setItem('username', this.loginForm.username);
          window.sessionStorage.setItem('realname', res.data.data.realName);
          this.$http.get("/datapoly/manager/api/v1/health/version").then(
            res => {
              if (0 === res.data.code) {
                window.sessionStorage.setItem('version', res.data.data);
                this.$message(this.$t('login.loginSuccess'));
                this.$router.push({ path: '/dashboard' });
              } else {
                this.$message(res.data.message);
              }
              this.resetLoading();
            }
          );
        } else {
          this.showMessageBox(this.$t('login.loginError') + res.data.message);
          this.resetLoading();
        }
      }).catch(error => {
        this.resetLoading();
        this.showMessageBox(this.$t('login.loginError') + (error || this.$t('login.connectionFailed')));
      })
    }
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #334155 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.login-form {
  background: rgba(15, 23, 42, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.3);
  width: 100%;
  max-width: 420px;
  padding: 40px;
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(59, 130, 246, 0.2);
}

.login-form::before {
  content: "";
  position: absolute;
  top: -50%;
  left: -50%;
  right: -50%;
  bottom: -50%;
  background: linear-gradient(
    45deg,
    transparent,
    rgba(59, 130, 246, 0.1),
    transparent
  );
  transform: rotate(45deg);
  z-index: 0;
}

.form-content {
  position: relative;
  z-index: 1;
}

.logo-section {
  text-align: center;
  margin-bottom: 30px;
}

.system-title {
  color: #e2e8f0;
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 8px 0;
  background: linear-gradient(135deg, #3b82f6 0%, #60a5fa 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.system-subtitle {
  color: #94a3b8;
  font-size: 14px;
  margin: 0;
}

.input-group {
  margin-bottom: 20px;
}

.input-label {
  display: block;
  margin-bottom: 8px;
  color: #e2e8f0;
  font-weight: 500;
  font-size: 14px;
}

.input-field {
  width: 90%;
  padding: 12px 16px;
  border: 2px solid #334155;
  border-radius: 8px;
  font-size: 16px;
  transition: all 0.3s ease;
  background: #1e293b;
  outline: none;
  color: #f1f5f9;
}

.input-field:focus {
  border-color: #3b82f6;
  background: #0f172a;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
}

.input-field:hover {
  border-color: #475569;
}

.input-field::placeholder {
  color: #64748b;
}

.input-error {
  border-color: #ef4444 !important;
  background: #1a0f0f;
}

.error-message {
  color: #ef4444;
  font-size: 12px;
  margin-top: 4px;
  display: block;
}

.checkbox-label {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: #94a3b8;
}

.checkbox-input {
  display: none;
}

.checkbox-custom {
  width: 18px;
  height: 18px;
  border: 2px solid #475569;
  border-radius: 4px;
  margin-right: 8px;
  position: relative;
  transition: all 0.3s ease;
  background: #1e293b;
}

.checkbox-input:checked + .checkbox-custom {
  background: #3b82f6;
  border-color: #3b82f6;
}

.checkbox-input:checked + .checkbox-custom::after {
  content: "✓";
  position: absolute;
  color: white;
  font-size: 12px;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.forgot-link {
  color: #3b82f6;
  text-decoration: none;
  transition: color 0.3s ease;
}

.forgot-link:hover {
  color: #60a5fa;
  text-decoration: underline;
}

.login-button {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.login-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(59, 130, 246, 0.3);
}

.login-button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}

.loading-text {
  display: inline-block;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

.footer-links {
  text-align: center;
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #334155;
}

.copyright {
  color: #64748b;
  font-size: 12px;
  margin: 0;
}

/* Responsive design */
@media (max-width: 480px) {
  .login-form {
    padding: 30px 20px;
    margin: 10px;
  }

  .system-title {
    font-size: 24px;
  }

  .remember-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .forgot-link {
    margin-left: 0;
  }
}

/* Dark mode adaptation (kept consistent) */
@media (prefers-color-scheme: dark) {
  .login-container {
    background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #334155 100%);
  }

  .login-form {
    background: rgba(15, 23, 42, 0.95);
  }
}
</style>