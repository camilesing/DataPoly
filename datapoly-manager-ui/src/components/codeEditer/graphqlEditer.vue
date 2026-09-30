<template>
  <div class="custom-code-script">
    <div class="quick-script-tag">
      <div class="mybatis-named-tag"
           @click="addSnippet('query')">type Query</div>
      <div class="mybatis-named-tag"
           @click="addSnippet('sql')">@sql</div>
      <div class="mybatis-named-tag"
           @click="addSqlTag('if')">if</div>
      <div class="mybatis-named-tag"
           @click="addSqlTag('foreach')">foreach</div>
    </div>
    <codemirror class="custom-code-mirror"
                ref="cmEditor"
                v-model="value"
                :options="cmOptions"
                @ready="onCmReady">
    </codemirror>
  </div>
</template>

<script>
import { codemirror } from 'vue-codemirror'

import 'codemirror/theme/darcula.css'
import 'codemirror/addon/hint/show-hint.css'
import 'codemirror/mode/sql/sql.js'

const CodeMirror = require('codemirror/lib/codemirror')
require('codemirror/addon/mode/simple.js')

// Lightweight GraphQL SDL highlighting (no codemirror-graphql dependency): keywords, directives,
// type names, strings and # comments; triple-quoted @sql blocks span lines via a blockstring state
CodeMirror.defineSimpleMode('graphql-sdl', {
  start: [
    { regex: /#.*/, token: 'comment' },
    { regex: /"""/, token: 'string', next: 'blockstring' },
    { regex: /"(?:[^"\\]|\\.)*"?/, token: 'string' },
    {
      regex: /\b(type|scalar|directive|enum|input|interface|union|schema|extend|implements|on|query|mutation|subscription|fragment|repeatable)\b/,
      token: 'keyword'
    },
    { regex: /@[A-Za-z_][\w]*/, token: 'meta' },
    { regex: /\b(true|false|null)\b/, token: 'atom' },
    { regex: /[A-Z][\w]*/, token: 'variable-2' },
    { regex: /[a-z_][\w]*(?=\s*:)/, token: 'property' },
    { regex: /[{}()\[\],!|=]/, token: 'operator' },
  ],
  blockstring: [
    { regex: /"""/, token: 'string', next: 'start' },
    { regex: /(?:[^"]|"(?!""))+/, token: 'string' },
    { regex: /"/, token: 'string' },
  ],
  meta: false
})

export default {
  name: 'graphqlEditer',
  components: {
    codemirror
  },
  data () {
    return {
      editor: null,
      value: '',
      cmOptions: {
        styleActiveLine: true,
        lineNumbers: true,
        mode: 'graphql-sdl',
        theme: 'darcula',
        lineWrapping: true,
        autofocus: true,
        matchBrackets: true,
        tabSize: 2,
        fontSize: 14
      },
    }
  },
  props: {
    content: {
      type: String,
      default: ''
    },
    editorHeightNum: {
      type: Number,
      default: 300
    }
  },
  methods: {
    onCmReady (cm) {
      this.editor = cm
      cm.setSize('100%', this.editorHeightNum + 'px')
    },
    addSnippet: function (kind) {
      let val = ''
      if (kind == 'query') {
        val = '\ntype Query {\n' +
          '  users(id: Long): [User]\n' +
          '    @sql(sql: """SELECT id, name FROM t_user WHERE 1=1\n' +
          '      <if test="id != null"> AND id = #{id}</if>""")\n' +
          '}\n\n' +
          'type User { id: Long  name: String }\n'
      } else if (kind == 'sql') {
        val = '\n  @sql(sql: """SELECT * FROM t WHERE 1=1\n    <if test=""></if>""")\n'
      }
      if (this.editor) {
        this.editor.replaceSelection(val)
      }
    },
    addSqlTag: function (tag) {
      let val = ''
      if (tag == 'foreach') {
        val = "\n<foreach open=\"(\" close=\")\" collection=\"\" separator=\",\" item=\"item\" index=\"index\">#{item}</foreach>"
      } else if (tag == 'if') {
        val = "\n<if test=\"\" ></if>"
      }
      if (this.editor) {
        this.editor.replaceSelection(val)
      }
    },
    queryContent: function () {
      return [this.value]
    },
    resetEditor: function (value) {
      this.value = value
      if (this.editor) {
        this.editor.setValue(value)
      }
    }
  },
  mounted () {
    this.value = this.content
  },
  watch: {
    editorHeightNum (newVal, OldVal) {
      if (this.editor) {
        this.editor.setSize('100%', newVal + 'px')
      }
    }
  },
}
</script>

<style scoped>
.custom-code-script {
  font-size: 13px;
  line-height: 150%;
}

.quick-script-tag {
  display: flex;
  float: right;
}

.quick-script-tag .mybatis-named-tag {
  background-color: #170f7cb9;
  color: #fff;
  border-radius: 3px;
  margin: 2px;
  line-height: 22px;
  padding: 0 5px;
  cursor: pointer;
}

.custom-code-mirror {
  font-size: 13px;
  line-height: 150%;
}
</style>
