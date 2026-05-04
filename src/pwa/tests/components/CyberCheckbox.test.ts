import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CyberCheckbox from '@/shared/ui/core/CyberCheckbox.vue'

describe('CyberCheckbox.vue', () => {
  it('renders unchecked state with label', () => {
    const wrapper = mount(CyberCheckbox, {
      props: { checked: false, label: 'MUTE ALL', focused: false }
    })
    
    expect(wrapper.text()).toContain('MUTE ALL')
    expect(wrapper.text()).toContain('☐')
    expect(wrapper.text()).not.toContain('☑')
  })

  it('renders checked state correctly', () => {
    const wrapper = mount(CyberCheckbox, {
      props: { checked: true, label: 'NIGHT MODE', focused: false }
    })
    
    expect(wrapper.text()).toContain('☑')
    expect(wrapper.text()).not.toContain('☐')
  })

  it('shows cursor arrow when focused', () => {
    const wrapper = mount(CyberCheckbox, {
      props: { checked: false, label: 'TEST', focused: true }
    })
    
    expect(wrapper.text()).toContain('▶')
  })

  it('hides cursor arrow when not focused', () => {
    const wrapper = mount(CyberCheckbox, {
      props: { checked: false, label: 'TEST', focused: false }
    })
    
    expect(wrapper.text()).not.toContain('▶')
  })

  it('renders sub-label when provided', () => {
    const wrapper = mount(CyberCheckbox, {
      props: { checked: false, label: 'MUTE', focused: false, subLabel: '(silence mode)' }
    })
    
    expect(wrapper.text()).toContain('(silence mode)')
  })
})
