import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import PerformancePanel from '@/views/panels/PerformancePanel.vue'

vi.mock('@halo-dev/components', () => ({
  VAlert: { template: '<div><slot /></div>' },
  VButton: { template: '<button><slot /></button>' },
  VEmpty: {
    props: ['title'],
    template: '<div>{{ title }}<slot name="message" /><slot name="actions" /></div>',
  },
  VLoading: { template: '<span />' },
}))

describe('PerformancePanel route ranking', () => {
  it('uses slow routes by default and updates the ranking copy after a selection', async () => {
    const wrapper = mount(PerformancePanel, {
      props: {
        pluginSort: 'load',
        pluginsLoading: false,
        routesLoading: false,
        routes: [],
      },
    })

    expect(wrapper.text()).toContain('慢接口排行')
    expect(wrapper.text()).toContain('持续高消耗插件')
    expect(wrapper.text()).toContain('慢请求阈值')

    await wrapper.find('select[aria-label="接口排行排序"]').setValue('p99')

    expect(wrapper.emitted('update:routeSort')).toEqual([['p99']])
    expect(wrapper.text()).toContain('接口 P99 排行')
    expect(wrapper.text()).toContain('所有归一化请求路径')
  })
})
