import { useEffect, useState } from 'react'
import { Form, Input, Modal } from 'antd'
import { registerWorkflowDefinition } from '@/api/workflowDefinition'
import { useMessageContext } from '@/components/providers/messageContext'
import type { WorkflowDef } from '@/types/workflow'

type CloneWorkflowDialogProps = {
  selectedWorkflow: WorkflowDef | null;
  workflowList: WorkflowDef[];
  onClose: () => void;
  onSuccess: () => void;
}

type CloneFormValues = {
  name: string
}

function nextCopyName(name: string, definitions: WorkflowDef[]) {
  const existingNames = new Set(definitions.map((definition) => definition.name))
  const baseName = `${name}_copy`
  if (!existingNames.has(baseName)) return baseName

  let index = 2
  while (existingNames.has(`${baseName}_${index}`)) index += 1
  return `${baseName}_${index}`
}

export function CloneWorkflowDialog({
  selectedWorkflow: definition,
  workflowList: definitions,
  onClose,
  onSuccess,
}: CloneWorkflowDialogProps) {
  const [form] = Form.useForm<CloneFormValues>()
  const { setMessage } = useMessageContext()
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!definition) return
    form.setFieldsValue({ name: nextCopyName(definition.name, definitions) })
    form.setFields([{ name: 'name', errors: [] }])
  }, [definition, definitions, form])

  const handleSubmit = async () => {
    if (!definition) return

    try {
      const { name } = await form.validateFields()
      const normalizedName = name.trim()
      if (definitions.some((item) => item.name === normalizedName)) {
        form.setFields([{ name: 'name', errors: [`工作流名称已存在：${normalizedName}`] }])
        return
      }

      setSubmitting(true)
      await registerWorkflowDefinition({
        ...definition,
        name: normalizedName,
        version: 1,
        displayName: definition.displayName
          ? `${definition.displayName} 副本`
          : normalizedName,
      })
      onSuccess()
    } catch (reason) {
      if (reason && typeof reason === 'object' && 'errorFields' in reason) return
      setMessage({
        severity: 'error',
        text: reason instanceof Error ? reason.message : '复制工作流失败',
      })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title="复制工作流"
      open={Boolean(definition)}
      okText="确认复制"
      cancelText="取消"
      confirmLoading={submitting}
      destroyOnHidden
      onCancel={onClose}
      onOk={() => void handleSubmit()}
    >
      <p>
        复制 <strong>{definition?.displayName?.trim() || definition?.name}</strong>{' '}
        的最新定义，副本版本将从 1 开始。
      </p>
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="name"
          label="工作流名称"
          rules={[
            { required: true, whitespace: true, message: '请输入工作流名称' },
          ]}
        >
          <Input autoFocus spellCheck={false} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
