import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import RouteSortControl from '@/components/performance/RouteSortControl.vue'

describe('RouteSortControl', () => {
  it('offers every supported route ranking and emits a typed selection', async () => {
    const wrapper = mount(RouteSortControl, {
      props: { modelValue: 'slow' },
    })

    expect(wrapper.findAll('option').map((option) => option.text())).toEqual([
      '慢接口',
      'P95',
      'P99',
      '平均',
      '最大',
      '请求量',
      '错误数',
      '错误率',
    ])

    await wrapper.find('select').setValue('error-rate')

    expect(wrapper.emitted('update:modelValue')).toEqual([['error-rate']])
  })
})
