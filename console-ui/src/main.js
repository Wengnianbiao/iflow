// src/main.js
import Vue from 'vue';
import App from './App.vue';
import ElementUI, { Message } from 'element-ui';
import 'element-ui/lib/theme-chalk/index.css';
import './assets/common.css';
import router from './router';

Vue.use(ElementUI);
Vue.config.productionTip = false;

// 统一 Message 默认 duration 为 5s
['error', 'warning', 'success', 'info'].forEach(type => {
  const original = Message[type];
  Message[type] = (options) => {
    if (typeof options === 'string') {
      options = { message: options };
    }
    options.duration = options.duration || 5000;
    return original(options);
  };
});
Vue.prototype.$message = Message;

new Vue({
  router,
  render: h => h(App),
}).$mount('#app');
