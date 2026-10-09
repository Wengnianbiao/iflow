import test from 'node:test'
import assert from 'node:assert/strict'
import { javaScriptSyntaxProblems } from '../src/pages/definition/EditorPanel/TaskFormTab/forms/javascriptDiagnostics.ts'

test('the malformed ternary in the INLINE editor points at the unexpected token', () => {
  const source = '(function () {\n  var patientId = 12;\n  sidwjdw?e ee\n  return{patientId:patientId};\n})()'
  const [problem] = javaScriptSyntaxProblems(source)
  assert.equal(source.slice(problem.start, problem.end), 'ee')
  assert.match(problem.message, /JavaScript 语法错误.*Unexpected token/)
  assert.equal(source.slice(0, problem.start).split('\n').length, 3)
})

test('fixing the syntax clears the error without requiring runtime variables to be declared', () => {
  assert.deepEqual(javaScriptSyntaxProblems('(function () {\n  var patientId = $.data.patientId;\n  return {patientId};\n})()'), [])
  assert.deepEqual(javaScriptSyntaxProblems('function scriptFun() { return $.response; }'), [])
  assert.deepEqual(javaScriptSyntaxProblems('XML.parse($.xml); Java.type("java.lang.String");'), [])
})

test('modern ECMAScript expressions, template strings and regexes are accepted', () => {
  assert.deepEqual(javaScriptSyntaxProblems('(() => { const id = $.data?.id ?? 0; return `${id}:${/\\D/g.test("a")}`; })()'), [])
  assert.deepEqual(javaScriptSyntaxProblems('async function f() { return await Promise.resolve(1); }'), [])
})

test('top-level return is rejected because the backend evaluates a script rather than a function body', () => {
  const [problem] = javaScriptSyntaxProblems('return $.response;')
  assert.match(problem.message, /return.*outside of function/)
})

test('unterminated strings are marked without underlining the following line', () => {
  const source = 'const text = "missing\nreturn text;'
  const [problem] = javaScriptSyntaxProblems(source)
  assert.equal(source.slice(problem.start, problem.end), '"missing')
  assert.match(problem.message, /Unterminated string/)
})

test('offsets remain accurate after Chinese text, emoji and CRLF line endings', () => {
  const source = 'const text = "中文😀";\r\nconst value = ;'
  const [problem] = javaScriptSyntaxProblems(source)
  assert.equal(source.slice(problem.start, problem.end), ';')
  assert.equal(problem.start, source.lastIndexOf(';'))
})

test('incomplete code at EOF gets a visible range and empty code has no errors', () => {
  const source = '(() => {\n  return 1;'
  const [problem] = javaScriptSyntaxProblems(source)
  assert.ok(problem.end > problem.start)
  assert.equal(problem.end, source.length)
  assert.deepEqual(javaScriptSyntaxProblems(''), [])
})

test('an unfinished block ending in blank lines still underlines a visible character', () => {
  const source = '(() => {\n\n  '
  const [problem] = javaScriptSyntaxProblems(source)
  assert.equal(source.slice(problem.start, problem.end), '{')
})

test('the syntax check never executes the supplied script', () => {
  assert.deepEqual(javaScriptSyntaxProblems('throw new Error("must not execute");'), [])
})
