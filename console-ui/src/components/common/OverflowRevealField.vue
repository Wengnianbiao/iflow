<template>
  <el-tooltip
    :content="displayText"
    placement="top"
    :disabled="!showTooltip"
    :value="showTooltip"
    manual
    :open-delay="0"
    :enterable="true"
    popper-class="overflow-reveal-tooltip"
  >
    <div
      ref="container"
      class="overflow-reveal-field"
      :class="{ 'is-active': isActive }"
      :style="containerStyle"
      @mouseenter="activate"
      @mouseleave="deactivate"
      @focusin="activate"
      @focusout="deactivate"
    >
      <slot />
    </div>
  </el-tooltip>
</template>

<script>
export default {
  name: 'OverflowRevealField',
  props: {
    value: {
      type: [String, Number],
      default: ''
    }
  },
  data() {
    return {
      isActive: false,
      showTooltip: false,
      containerStyle: {}
    };
  },
  computed: {
    displayText() {
      if (this.value === null || this.value === undefined) {
        return '';
      }
      return String(this.value);
    }
  },
  methods: {
    getInputEl() {
      return this.$refs.container?.querySelector('.el-input__inner');
    },
    isFlexContext() {
      return !!this.$refs.container?.closest('.header-row');
    },
    getMaxExpandWidth() {
      const row = this.$refs.container?.closest('.header-row, .el-form-item__content');
      if (!row) {
        return 480;
      }
      return Math.max(row.clientWidth - 16, 200);
    },
    measureTextWidth(text, input) {
      const style = window.getComputedStyle(input);
      const canvas = document.createElement('canvas');
      const context = canvas.getContext('2d');
      context.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`;
      return context.measureText(text).width;
    },
    isTextOverflow(input, text) {
      if (!input || !text) {
        return false;
      }
      const style = window.getComputedStyle(input);
      const padding = parseFloat(style.paddingLeft) + parseFloat(style.paddingRight);
      const available = input.clientWidth - padding;
      return this.measureTextWidth(text, input) > available + 1;
    },
    activate() {
      if (!this.displayText) {
        return;
      }

      this.isActive = true;
      this.$nextTick(() => {
        const input = this.getInputEl();
        if (!input) {
          return;
        }

        const overflow = this.isTextOverflow(input, this.displayText);
        if (!overflow) {
          this.showTooltip = false;
          this.containerStyle = {};
          return;
        }

        this.showTooltip = true;

        if (this.isFlexContext()) {
          const maxWidth = this.getMaxExpandWidth();
          const textWidth = this.measureTextWidth(this.displayText, input);
          const style = window.getComputedStyle(input);
          const extra = parseFloat(style.paddingLeft) + parseFloat(style.paddingRight) + 32;
          const desiredWidth = Math.min(textWidth + extra, maxWidth);
          this.containerStyle = {
            flex: `1 1 ${desiredWidth}px`,
            minWidth: `${desiredWidth}px`
          };
        } else {
          this.containerStyle = {};
        }

        this.$nextTick(() => {
          const activeInput = this.getInputEl();
          if (activeInput) {
            activeInput.scrollLeft = 0;
          }
        });
      });
    },
    isInteracting() {
      const container = this.$refs.container;
      if (!container) {
        return false;
      }
      if (container.matches(':hover')) {
        return true;
      }
      if (container.contains(document.activeElement)) {
        return true;
      }
      const tooltip = document.querySelector('.overflow-reveal-tooltip');
      return !!(tooltip && tooltip.matches(':hover'));
    },
    deactivate() {
      window.setTimeout(() => {
        const active = document.activeElement;
        const inDropdown = active && active.closest('.el-select-dropdown');
        if (inDropdown) {
          return;
        }
        if (!this.isInteracting()) {
          this.reset();
        }
      }, 80);
    },
    reset() {
      this.isActive = false;
      this.showTooltip = false;
      this.containerStyle = {};
    }
  }
};
</script>

<style scoped>
.overflow-reveal-field {
  min-width: 0;
  width: 100%;
}

.overflow-reveal-field.is-active {
  position: relative;
  z-index: 2;
}

.overflow-reveal-field.is-active ::v-deep .el-input__inner {
  text-overflow: clip;
  overflow-x: auto;
}
</style>

<style>
.overflow-reveal-tooltip {
  max-width: 80vw;
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
