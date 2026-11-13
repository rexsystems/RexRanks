import { createRouter, createWebHashHistory } from "vue-router";
import GroupsView from "./views/GroupsView.vue";
import UsersView from "./views/UsersView.vue";
import TracksView from "./views/TracksView.vue";
import ColorStudio from "./views/ColorStudio.vue";
import SearchView from "./views/SearchView.vue";
import ChangesView from "./views/ChangesView.vue";

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", redirect: "/groups" },
    { path: "/groups/:name?", component: GroupsView },
    { path: "/users/:uuid?", component: UsersView },
    { path: "/tracks/:name?", component: TracksView },
    { path: "/color", component: ColorStudio },
    { path: "/search", component: SearchView },
    { path: "/changes", component: ChangesView },
    { path: "/s/:id", redirect: "/groups" },
  ],
});
