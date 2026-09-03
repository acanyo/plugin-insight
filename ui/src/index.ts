import { definePlugin } from '@halo-dev/ui-shared'
import { markRaw } from 'vue'
import RiPulseLine from '~icons/ri/pulse-line'

import { INSIGHT_VIEW_PERMISSION } from '@/constants/permissions'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/insight',
        name: 'Insight',
        component: () => import(/* webpackChunkName: "HomeView" */ './views/HomeView.vue'),
        meta: {
          title: '运行监控',
          searchable: true,
          permissions: [INSIGHT_VIEW_PERMISSION],
          menu: {
            name: '运行监控',
            group: 'tool',
            icon: markRaw(RiPulseLine),
            priority: 30,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
