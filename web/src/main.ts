import { createApp } from "vue";
import App from "./App.vue";
import { router } from "./router";
import { boot } from "./store";
import "./styles.css";

boot();
createApp(App).use(router).mount("#app");
