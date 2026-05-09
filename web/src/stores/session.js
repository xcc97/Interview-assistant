import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { getCurrentUser, login as loginApi } from '../api';

const TOKEN_KEY = 'ia_access_token';

function normalizeUser(profile) {
  if (!profile) {
    return null;
  }
  return {
    id: profile.userId,
    nickname: profile.nickname,
    phone: profile.phone,
    role: profile.role,
    currentPlan: profile.currentPlanName,
    remainingMinutes: profile.remainingMinutes,
    usedMinutes: profile.usedMinutes,
    expiryDate: profile.expiryTime,
  };
}

export const useSessionStore = defineStore('session', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const user = ref(null);
  const loading = ref(false);

  const isAuthenticated = computed(() => Boolean(token.value));
  const isAdmin = computed(() => user.value?.role === 'ADMIN');

  async function login(payload) {
    const result = await loginApi(payload);
    token.value = result.accessToken;
    user.value = normalizeUser(result.user);
    localStorage.setItem(TOKEN_KEY, token.value);
    return result;
  }

  async function restore() {
    if (!token.value) {
      user.value = null;
      return;
    }
    loading.value = true;
    try {
      const profile = await getCurrentUser();
      user.value = normalizeUser(profile);
    } catch (error) {
      logout();
      throw error;
    } finally {
      loading.value = false;
    }
  }

  function logout() {
    token.value = '';
    user.value = null;
    localStorage.removeItem(TOKEN_KEY);
  }

  return {
    token,
    user,
    loading,
    isAuthenticated,
    isAdmin,
    login,
    restore,
    logout,
  };
});
