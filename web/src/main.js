import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { useSessionStore } from './stores/session';
import './styles.css';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);

const session = useSessionStore();
session.restore().catch(() => undefined).finally(() => {
  app.use(router);
  app.mount('#app');
});
