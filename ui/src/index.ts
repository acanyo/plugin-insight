import { definePlugin } from '@halo-dev/ui-shared'
import { markRaw } from 'vue'
import RiRadarLine from '~icons/ri/radar-line'

import { INSIGHT_VIEW_PERMISSION } from '@/constants/permissions'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/insight',
        name: 'Insight',
        component: () =>
          import(/* webpackChunkName: "InsightDashboardView" */ './views/InsightDashboardView.vue'),
        meta: {
          title: '运行洞察',
          searchable: true,
          permissions: [INSIGHT_VIEW_PERMISSION],
          menu: {
            name: '运行洞察',
            group: 'tool',
            icon: markRaw(RiRadarLine),
            priority: 30,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
