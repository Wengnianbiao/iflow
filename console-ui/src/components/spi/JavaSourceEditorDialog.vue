<template>
  <el-dialog
    :visible.sync="dialogVisible"
    :width="fullscreen ? '100%' : '80%'"
    :top="fullscreen ? '0' : '3vh'"
    :fullscreen="fullscreen"
    :close-on-click-modal="false"
    append-to-body
    @opened="initCodemirror"
  >
    <template slot="title">
      <span>{{ title }}</span>
      <el-button
        size="mini"
        :icon="fullscreen ? 'el-icon-copy-document' : 'el-icon-full-screen'"
        circle
        style="margin-left: 12px"
        @click="toggleFullscreen"
      ></el-button>
    </template>
    <div class="code-editor-container" :style="fullscreen ? 'height: calc(100vh - 120px)' : ''">
      <div class="code-toolbar">
        <div class="code-editor-stats">
          <el-tag size="mini" type="success" effect="plain">CodeMirror</el-tag>
          <el-tag size="mini" effect="plain">Java</el-tag>
          <el-tag size="mini" effect="plain">{{ editorStats.lines }} 行</el-tag>
          <el-tag size="mini" effect="plain">{{ editorStats.chars }} 字符</el-tag>
        </div>
        <div class="code-toolbar-actions">
          <el-button size="mini" @click="$emit('apply-template')" icon="el-icon-document">使用模板</el-button>
        </div>
      </div>
      <div class="code-editor-wrapper">
        <textarea ref="codemirrorArea"></textarea>
      </div>
      <div class="code-editor-footer">
        <el-button size="small" @click="close">取消</el-button>
        <el-button size="small" type="primary" @click="confirm">确定</el-button>
      </div>
    </div>
    <div slot="footer" style="display:none;"></div>
  </el-dialog>
</template>

<script>
import CodeMirror from 'codemirror';
import 'codemirror/lib/codemirror.css';
import 'codemirror/theme/material-darker.css';
import 'codemirror/mode/clike/clike';
import 'codemirror/addon/edit/matchbrackets';
import 'codemirror/addon/edit/closebrackets';
import 'codemirror/addon/fold/foldcode';
import 'codemirror/addon/fold/foldgutter';
import 'codemirror/addon/fold/foldgutter.css';
import 'codemirror/addon/fold/brace-fold';
import 'codemirror/addon/fold/comment-fold';
import 'codemirror/addon/selection/active-line';

export default {
  name: 'JavaSourceEditorDialog',
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: '' },
    code: { type: String, default: '' }
  },
  data() {
    return {
      fullscreen: false,
      localCode: '',
      cmInstance: null
    };
  },
  computed: {
    dialogVisible: {
      get() {
        return this.visible;
      },
      set(v) {
        this.$emit('update:visible', v);
      }
    },
    editorStats() {
      const code = this.localCode || '';
      return {
        lines: Math.max((code.match(/\n/g) || []).length + 1, 1),
        chars: code.length
      };
    }
  },
  watch: {
    code(val) {
      if (this.cmInstance && val !== this.cmInstance.getValue()) {
        this.cmInstance.setValue(val || '');
        this.localCode = val || '';
      }
    },
    visible(val) {
      if (val) {
        this.localCode = this.code || '';
        this.fullscreen = false;
      }
    }
  },
  beforeDestroy() {
    if (this.cmInstance) {
      this.cmInstance.toTextArea();
      this.cmInstance = null;
    }
  },
  methods: {
    initCodemirror() {
      this.$nextTick(() => {
        this.localCode = this.code || '';
        if (this.cmInstance) {
          this.cmInstance.setValue(this.localCode);
          this.cmInstance.refresh();
          this.cmInstance.focus();
          return;
        }
        this.cmInstance = CodeMirror.fromTextArea(this.$refs.codemirrorArea, {
          mode: 'text/x-java',
          theme: 'material-darker',
          lineNumbers: true,
          matchBrackets: true,
          autoCloseBrackets: true,
          foldGutter: true,
          gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
          styleActiveLine: true,
          indentUnit: 4,
          tabSize: 4,
          indentWithTabs: false,
          lineWrapping: false,
          extraKeys: {
            Tab: (cm) => {
              if (cm.somethingSelected()) {
                cm.indentSelection('add');
              } else {
                cm.replaceSelection('    ', 'end');
              }
            },
            'Shift-Tab': (cm) => cm.indentSelection('subtract')
          }
        });
        this.cmInstance.setValue(this.localCode);
        this.cmInstance.on('change', (cm) => {
          this.localCode = cm.getValue();
          this.$emit('update:code', this.localCode);
        });
        this.cmInstance.setSize('100%', '100%');
        this.cmInstance.focus();
      });
    },
    toggleFullscreen() {
      this.fullscreen = !this.fullscreen;
      this.$nextTick(() => {
        if (this.cmInstance) this.cmInstance.refresh();
      });
    },
    /** 父组件套用模板后调用，同步编辑器内容 */
    setEditorValue(text) {
      this.localCode = text || '';
      this.$emit('update:code', this.localCode);
      if (this.cmInstance) {
        this.cmInstance.setValue(this.localCode);
      }
    },
    close() {
      this.dialogVisible = false;
    },
    confirm() {
      const finalCode = this.cmInstance ? this.cmInstance.getValue() : this.localCode;
      this.$emit('update:code', finalCode);
      this.$emit('confirm', finalCode);
      this.dialogVisible = false;
    }
  }
};
</script>

<style scoped>
.code-editor-container {
  display: flex;
  flex-direction: column;
  height: calc(80vh - 120px);
}

.code-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  flex-shrink: 0;
}

.code-editor-stats {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.code-editor-wrapper {
  border: 1px solid #d1d5db;
  border-radius: 4px;
  overflow: hidden;
  flex: 1;
  min-height: 0;
}

.code-editor-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 12px;
  flex-shrink: 0;
}
</style>

<style>
.code-editor-wrapper .CodeMirror {
  height: 100%;
  font-family: 'JetBrains Mono', 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>
