// Use of this source code is governed by a BSD-style license

/**
 * Sizes a table's 操作 column to the buttons it actually has to show.
 *
 * Element treats a column without a numeric `width` as flexible: `updateColumnsWidth` puts
 * every column whose `width` is not a number into `flexColumns` and hands them a share of
 * whatever room the table does not need for its minimums. The operation columns were
 * declared with `min-width` only — and on most menu pages with `min-width="35%"`, which
 * `parseInt` turns into 35px — so the column had no width of its own: on a wide screen it
 * grew far past its buttons (the interface list reached 650px for buttons that need ~510)
 * and on a narrow one it was squeezed until the buttons wrapped onto a second line.
 *
 * A component using this mixin:
 *   - renders its row actions in an `el-button-group` (that is what gets measured), and
 *   - binds `:width="operationColumnWidth"` on the operation column (no `min-width`).
 *
 * The width is re-measured after every render — data load, pagination, a language switch
 * (English labels are wider and are re-rendered in place) — from the widest row, and only
 * written when it changed, so the measure/render loop settles after one pass. Buttons keep
 * their natural width even while wrapped, so summing them is correct at any column width.
 */
const CELL_PADDING = 22 // .el-table .cell's 10px side paddings plus the 1px borders

export default {
  data () {
    return {
      // Only the first-paint value (it fits the widest button set); the measurement
      // replaces it in the same tick the rows render.
      operationColumnWidth: 600
    }
  },
  mounted () {
    this.measureOperationColumn()
  },
  updated () {
    this.measureOperationColumn()
  },
  methods: {
    measureOperationColumn () {
      this.$nextTick(() => {
        if (!this.$el || !this.$el.querySelectorAll) {
          return
        }
        let widest = 0
        this.$el.querySelectorAll('.el-table__body-wrapper .el-button-group').forEach(group => {
          const buttons = group.querySelectorAll('.el-button')
          let width = 0
          buttons.forEach((button, index) => {
            // a group's buttons overlap their neighbour by 1px (margin-left: -1px)
            width += button.getBoundingClientRect().width - (index === 0 ? 0 : 1)
          })
          if (width > widest) {
            widest = width
          }
        })
        if (widest <= 0) {
          return
        }
        const width = Math.ceil(widest) + CELL_PADDING
        // 1px of tolerance: sub-pixel rendering shifts the sum by a fraction, and writing
        // it back would re-render, measure and write again on every paint
        if (Math.abs(width - this.operationColumnWidth) > 1) {
          this.operationColumnWidth = width
        }
      })
    }
  }
}