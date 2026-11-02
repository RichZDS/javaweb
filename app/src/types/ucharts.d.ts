// @qiun/ucharts 没有自带类型声明；只在 components/UChart.vue 里用到构造函数和 showToolTip
declare module '@qiun/ucharts' {
  const uCharts: new (opts: Record<string, unknown>) => {
    showToolTip: (e: unknown) => void
    updateData: (data: Record<string, unknown>) => void
  }
  export default uCharts
}
